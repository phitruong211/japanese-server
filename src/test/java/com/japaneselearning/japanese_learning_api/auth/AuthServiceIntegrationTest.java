package com.japaneselearning.japanese_learning_api.auth;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.DomainTypes.UserStatus;
import com.japaneselearning.japanese_learning_api.model.User;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:auth-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AuthServiceIntegrationTest {
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;
    @Autowired EntityManager entityManager;

    @Test
    void registerCreatesUserSettingsAndTokens() {
        String email = "register-" + System.nanoTime() + "@example.com";

        AuthDtos.AuthResponse response = authService.register(
                new AuthDtos.RegisterRequest(email, "TestPassword123!", "Test User", "integration-test"));
        entityManager.flush();

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo(email);
        assertThat(response.user().displayName()).isEqualTo("Test User");
    }

    @Test
    void registerEndpointReturnsJsonAuthResponse() throws Exception {
        String email = "register-http-" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"TestPassword123!",\
                                "displayName":"Test User","deviceName":"integration-test"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
        entityManager.flush();
    }

    private AuthDtos.AuthResponse registerUser() {
        return authService.register(new AuthDtos.RegisterRequest(
                "status-" + System.nanoTime() + "@example.com", "TestPassword123!", "Status", "integration-test"));
    }

    private void setStatus(AuthDtos.AuthResponse response, UserStatus status) {
        entityManager.flush();
        entityManager.find(User.class, response.user().id()).setStatus(status);
        entityManager.flush();
    }

    @Test
    void refreshRotatesTokenForActiveUser() {
        AuthDtos.AuthResponse session = registerUser();
        AuthDtos.AuthResponse renewed = authService.refresh(new AuthDtos.RefreshRequest(session.refreshToken(), "integration-test"));
        assertThat(renewed.refreshToken()).isNotEqualTo(session.refreshToken());
    }

    @Test
    void refreshRejectsLockedAndDisabledUsersWithoutRevokingOrRotating() {
        for (UserStatus blocked : new UserStatus[] {UserStatus.LOCKED, UserStatus.DISABLED}) {
            AuthDtos.AuthResponse session = registerUser();
            setStatus(session, blocked);
            var request = new AuthDtos.RefreshRequest(session.refreshToken(), "integration-test");
            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(401));
            // Restoring the account proves the failed attempt did not consume the token.
            setStatus(session, UserStatus.ACTIVE);
            assertThat(authService.refresh(request).accessToken()).isNotBlank();
        }
    }

    @Test
    void meRejectsInactiveUser() {
        AuthDtos.AuthResponse session = registerUser();
        assertThat(authService.me(session.user().id()).email()).isEqualTo(session.user().email());
        setStatus(session, UserStatus.LOCKED);
        assertThatThrownBy(() -> authService.me(session.user().id()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(401));
    }
}

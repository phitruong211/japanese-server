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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
}

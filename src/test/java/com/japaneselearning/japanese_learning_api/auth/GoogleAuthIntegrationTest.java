package com.japaneselearning.japanese_learning_api.auth;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:google-auth-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@Transactional
class GoogleAuthIntegrationTest {
    @Autowired AuthService authService;
    @MockitoBean GoogleTokenVerifier verifier;

    private GoogleTokenVerifier.GoogleIdentity identity(String email) {
        return new GoogleTokenVerifier.GoogleIdentity("sub-" + email, email, "Google User", "https://example.com/a.png");
    }

    @Test
    void firstGoogleLoginCreatesAccountAndSecondReusesIt() {
        String email = "g-" + System.nanoTime() + "@gmail.com";
        when(verifier.verify("token")).thenReturn(identity(email));

        AuthDtos.AuthResponse first = authService.loginWithGoogle(new AuthDtos.GoogleLoginRequest("token", "test"));
        AuthDtos.AuthResponse second = authService.loginWithGoogle(new AuthDtos.GoogleLoginRequest("token", "test"));

        assertThat(first.user().googleLinked()).isTrue();
        assertThat(first.user().email()).isEqualTo(email);
        assertThat(second.user().id()).isEqualTo(first.user().id());
    }

    @Test
    void googleLoginDoesNotTakeOverExistingPasswordAccount() {
        String email = "p-" + System.nanoTime() + "@gmail.com";
        authService.register(new AuthDtos.RegisterRequest(email, "TestPassword123!", "Owner", "test"));
        when(verifier.verify("token")).thenReturn(identity(email));

        assertThatThrownBy(() -> authService.loginWithGoogle(new AuthDtos.GoogleLoginRequest("token", "test")))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("GOOGLE_NOT_LINKED");
    }

    @Test
    void linkedPasswordAccountCanThenSignInWithGoogle() {
        String email = "l-" + System.nanoTime() + "@gmail.com";
        AuthDtos.AuthResponse account = authService.register(new AuthDtos.RegisterRequest(email, "TestPassword123!", "Owner", "test"));
        when(verifier.verify("token")).thenReturn(identity(email));

        assertThat(authService.linkGoogle(account.user().id(), "token").googleLinked()).isTrue();
        assertThat(authService.loginWithGoogle(new AuthDtos.GoogleLoginRequest("token", "test")).user().id()).isEqualTo(account.user().id());
    }

    @Test
    void googleIdentityCannotBeLinkedToTwoAccounts() {
        String email = "d-" + System.nanoTime() + "@gmail.com";
        when(verifier.verify("token")).thenReturn(identity(email));
        authService.loginWithGoogle(new AuthDtos.GoogleLoginRequest("token", "test"));
        AuthDtos.AuthResponse other = authService.register(new AuthDtos.RegisterRequest("o-" + email, "TestPassword123!", "Other", "test"));

        assertThatThrownBy(() -> authService.linkGoogle(other.user().id(), "token"))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("GOOGLE_ALREADY_LINKED");
    }

    @Test
    void verifierRejectsWhenClientIdMissing() {
        assertThatThrownBy(() -> new GoogleTokenVerifier("").verify("x"))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).code()).isEqualTo("GOOGLE_DISABLED");
    }

    @Test
    void verifierRejectsGarbageToken() {
        assertThatThrownBy(() -> new GoogleTokenVerifier("client").verify("not-a-jwt")).isInstanceOf(ApiException.class);
    }
}

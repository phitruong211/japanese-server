package com.japaneselearning.japanese_learning_api.auth;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Verifies Google Identity Services ID tokens: signature (Google JWKS), expiry, issuer, audience, verified email. */
@Component
public class GoogleTokenVerifier {
    public record GoogleIdentity(String subject, String email, String displayName, String pictureUrl) {}

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final String clientId;
    private final JwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId.trim();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), jwt ->
                ISSUERS.contains(jwt.getClaimAsString("iss")) && jwt.getAudience() != null && jwt.getAudience().contains(this.clientId)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong issuer or audience", null))));
        this.decoder = nimbus;
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED", "Đăng nhập Google chưa được cấu hình trên máy chủ.");
        Jwt jwt;
        try { jwt = decoder.decode(idToken); }
        catch (JwtException ex) { throw ApiException.unauthorized("Phiên đăng nhập Google không hợp lệ hoặc đã hết hạn. Hãy thử lại."); }
        String email = jwt.getClaimAsString("email");
        if (email == null || !"true".equalsIgnoreCase(String.valueOf(jwt.getClaims().get("email_verified")))) {
            throw ApiException.unauthorized("Email Google chưa được xác minh.");
        }
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
        return new GoogleIdentity(jwt.getSubject(), email, name.length() > 100 ? name.substring(0, 100) : name, jwt.getClaimAsString("picture"));
    }
}

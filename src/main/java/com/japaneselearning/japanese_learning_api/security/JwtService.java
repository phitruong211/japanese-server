package com.japaneselearning.japanese_learning_api.security;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.japaneselearning.japanese_learning_api.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long accessTokenMinutes;

    public JwtService(ObjectMapper mapper,
                      @Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must contain at least 32 bytes");
        }
        this.mapper = mapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(accessTokenMinutes, ChronoUnit.MINUTES).getEpochSecond());
        try {
            String header = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String body = encode(mapper.writeValueAsBytes(payload));
            String unsigned = header + "." + body;
            return unsigned + "." + encode(sign(unsigned));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create access token", ex);
        }
    }

    public Optional<Claims> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = URL_DECODER.decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) return Optional.empty();
            Map<String, Object> payload = mapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            String subject = String.valueOf(payload.get("sub"));
            long expiresAt = ((Number) payload.get("exp")).longValue();
            String role = String.valueOf(payload.get("role"));
            if (Instant.now().getEpochSecond() >= expiresAt) return Optional.empty();
            return Optional.of(new Claims(subject, role));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public long accessTokenSeconds() { return accessTokenMinutes * 60; }

    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] bytes) { return URL_ENCODER.encodeToString(bytes); }
    public record Claims(String subject, String role) {}
}

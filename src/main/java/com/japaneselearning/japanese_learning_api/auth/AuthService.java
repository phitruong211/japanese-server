package com.japaneselearning.japanese_learning_api.auth;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.RefreshToken;
import com.japaneselearning.japanese_learning_api.model.User;
import com.japaneselearning.japanese_learning_api.model.UserSettings;
import com.japaneselearning.japanese_learning_api.repository.RefreshTokenRepository;
import com.japaneselearning.japanese_learning_api.repository.UserRepository;
import com.japaneselearning.japanese_learning_api.repository.UserSettingsRepository;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository users, UserSettingsRepository settings, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        this.users = users;
        this.settings = settings;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("Email đã được sử dụng");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        // Flush the parent first. UserSettings uses @MapsId, so Hibernate must
        // know that the User is persistent before it inserts the settings row.
        users.saveAndFlush(user);
        UserSettings preferences = new UserSettings();
        preferences.setUser(user);
        settings.saveAndFlush(preferences);
        return issue(user, request.deviceName());
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> ApiException.unauthorized("Email hoặc mật khẩu không đúng"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getStatus() != com.japaneselearning.japanese_learning_api.model.DomainTypes.UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Email hoặc mật khẩu không đúng");
        }
        user.setLastLoginAt(Instant.now());
        return issue(user, request.deviceName());
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token không hợp lệ"));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token đã hết hạn hoặc bị thu hồi");
        }
        stored.setRevokedAt(Instant.now());
        return issue(stored.getUser(), request.deviceName());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(UUID userId) {
        return toResponse(users.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng")));
    }

    private AuthDtos.AuthResponse issue(User user, String deviceName) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setDeviceName(deviceName == null ? null : deviceName.trim());
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
        refreshTokens.save(refreshToken);
        return new AuthDtos.AuthResponse(jwtService.createAccessToken(user), rawRefreshToken,
                jwtService.accessTokenSeconds(), toResponse(user));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private AuthDtos.UserResponse toResponse(User user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                user.getRole().name(), user.getLocale(), user.getTimezone(), user.getCreatedAt());
    }
}

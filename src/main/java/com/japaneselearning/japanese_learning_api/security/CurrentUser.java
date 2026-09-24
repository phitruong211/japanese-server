package com.japaneselearning.japanese_learning_api.security;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw ApiException.unauthorized("Bạn cần đăng nhập");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("Access token không hợp lệ");
        }
    }
}

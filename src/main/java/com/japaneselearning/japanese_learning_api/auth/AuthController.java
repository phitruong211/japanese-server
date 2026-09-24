package com.japaneselearning.japanese_learning_api.auth;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) { return authService.login(request); }

    @PostMapping("/refresh")
    AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) { return authService.refresh(request); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody AuthDtos.LogoutRequest request) { authService.logout(request.refreshToken()); }

    @GetMapping("/me")
    AuthDtos.UserResponse me(Authentication authentication) { return authService.me(CurrentUser.id(authentication)); }
}

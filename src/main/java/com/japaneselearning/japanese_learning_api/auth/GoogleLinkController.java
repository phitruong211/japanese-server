package com.japaneselearning.japanese_learning_api.auth;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Outside /auth/** so it requires a signed-in user. */
@RestController
@RequestMapping("/api/v1/users/me/google")
public class GoogleLinkController {
    private final AuthService authService;
    public GoogleLinkController(AuthService authService) { this.authService = authService; }

    @PostMapping
    AuthDtos.UserResponse link(Authentication authentication, @Valid @RequestBody AuthDtos.GoogleLinkRequest request) {
        return authService.linkGoogle(CurrentUser.id(authentication), request.idToken());
    }
}

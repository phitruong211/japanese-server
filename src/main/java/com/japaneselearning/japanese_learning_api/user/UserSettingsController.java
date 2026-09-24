package com.japaneselearning.japanese_learning_api.user;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.UserSettings;
import com.japaneselearning.japanese_learning_api.repository.UserSettingsRepository;
import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/settings")
public class UserSettingsController {
    private final UserSettingsRepository repository;
    public UserSettingsController(UserSettingsRepository repository) { this.repository = repository; }

    @GetMapping @Transactional(readOnly = true)
    public UserSettingsDtos.Response get(Authentication auth) { return response(find(CurrentUser.id(auth))); }

    @PatchMapping @Transactional
    public UserSettingsDtos.Response update(Authentication auth, @Valid @RequestBody UserSettingsDtos.UpdateRequest request) {
        UserSettings settings = find(CurrentUser.id(auth));
        if (request.theme() != null) settings.setTheme(request.theme());
        if (request.fontSize() != null) settings.setFontSize(request.fontSize());
        if (request.showFurigana() != null) settings.setShowFurigana(request.showFurigana());
        if (request.autoPlayAudio() != null) settings.setAutoPlayAudio(request.autoPlayAudio());
        if (request.dailyGoal() != null) settings.setDailyGoal(request.dailyGoal());
        if (request.ankiSessionMinutes() != null) settings.setAnkiSessionMinutes(request.ankiSessionMinutes());
        if (request.reducedMotion() != null) settings.setReducedMotion(request.reducedMotion());
        return response(settings);
    }

    private UserSettings find(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy cài đặt"));
    }

    private UserSettingsDtos.Response response(UserSettings s) {
        return new UserSettingsDtos.Response(s.getTheme(), s.getFontSize(), s.isShowFurigana(), s.isAutoPlayAudio(),
                s.getDailyGoal(), s.getAnkiSessionMinutes(), s.isReducedMotion());
    }
}

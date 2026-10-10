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
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;

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
        int nextAgain = request.srsAgainMinutes() == null ? settings.getSrsAgainMinutes() : request.srsAgainMinutes();
        int nextGood = request.srsGoodMinutes() == null ? settings.getSrsGoodMinutes() : request.srsGoodMinutes();
        if (nextGood <= nextAgain) throw new ApiException(HttpStatus.BAD_REQUEST, "Thời gian nhớ phải lớn hơn thời gian quên");
        BigDecimal nextRetention = request.srsDesiredRetention() == null ? settings.getSrsDesiredRetention() : request.srsDesiredRetention();
        if (!Set.of(new BigDecimal("0.900"), new BigDecimal("0.930"), new BigDecimal("0.950")).contains(nextRetention.setScale(3)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mức ghi nhớ chỉ hỗ trợ 90%, 93% hoặc 95%");
        if (request.theme() != null) settings.setTheme(request.theme());
        if (request.fontSize() != null) settings.setFontSize(request.fontSize());
        if (request.showFurigana() != null) {
            settings.setShowFurigana(request.showFurigana());
            settings.setShowFuriganaFront(request.showFurigana());
            settings.setShowFuriganaBack(request.showFurigana());
        }
        if (request.showFuriganaFront() != null) settings.setShowFuriganaFront(request.showFuriganaFront());
        if (request.showFuriganaBack() != null) settings.setShowFuriganaBack(request.showFuriganaBack());
        if (request.autoPlayAudio() != null) settings.setAutoPlayAudio(request.autoPlayAudio());
        if (request.dailyGoal() != null) settings.setDailyGoal(request.dailyGoal());
        if (request.ankiSessionMinutes() != null) settings.setAnkiSessionMinutes(request.ankiSessionMinutes());
        settings.setSrsAgainMinutes(nextAgain);
        settings.setSrsGoodMinutes(nextGood);
        settings.setSrsDesiredRetention(nextRetention.setScale(3));
        if (request.reducedMotion() != null) settings.setReducedMotion(request.reducedMotion());
        return response(settings);
    }

    private UserSettings find(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy cài đặt"));
    }

    private UserSettingsDtos.Response response(UserSettings s) {
        return new UserSettingsDtos.Response(s.getTheme(), s.getFontSize(), s.isShowFurigana(), s.isAutoPlayAudio(),
                s.getDailyGoal(), s.getAnkiSessionMinutes(), s.isReducedMotion(),
                s.isShowFuriganaFront(), s.isShowFuriganaBack(), s.getSrsAgainMinutes(),
                s.getSrsGoodMinutes(), s.getSrsDesiredRetention());
    }
}

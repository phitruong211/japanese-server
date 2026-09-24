package com.japaneselearning.japanese_learning_api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public final class UserSettingsDtos {
    private UserSettingsDtos() {}

    public record UpdateRequest(
            @Pattern(regexp = "light|dark|reading|high-contrast") String theme,
            @Pattern(regexp = "small|medium|large") String fontSize,
            Boolean showFurigana,
            Boolean autoPlayAudio,
            @Min(1) @Max(1000) Integer dailyGoal,
            @Min(0) @Max(180) Integer ankiSessionMinutes,
            Boolean reducedMotion) {}

    public record Response(String theme, String fontSize, boolean showFurigana, boolean autoPlayAudio,
                           int dailyGoal, int ankiSessionMinutes, boolean reducedMotion) {}
}

package com.japaneselearning.japanese_learning_api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public final class UserSettingsDtos {
    private UserSettingsDtos() {}

    public record UpdateRequest(
            @Pattern(regexp = "light|dark|reading|high-contrast") String theme,
            @Pattern(regexp = "small|medium|large") String fontSize,
            Boolean showFurigana,
            Boolean showFuriganaFront,
            Boolean showFuriganaBack,
            Boolean autoPlayAudio,
            @Min(1) @Max(1000) Integer dailyGoal,
            @Min(0) @Max(180) Integer ankiSessionMinutes,
            @Min(0) Integer srsDailyNewLimit,
            @Min(1) @Max(30) Integer srsAgainMinutes,
            @Min(2) @Max(720) Integer srsGoodMinutes,
            @DecimalMin("0.900") @DecimalMax("0.950") BigDecimal srsDesiredRetention,
            Boolean reducedMotion) {}

    public record Response(String theme, String fontSize, boolean showFurigana, boolean autoPlayAudio,
                           int dailyGoal, int ankiSessionMinutes, boolean reducedMotion,
                           boolean showFuriganaFront, boolean showFuriganaBack,
                           int srsDailyNewLimit,
                           int srsAgainMinutes, int srsGoodMinutes, BigDecimal srsDesiredRetention) {}
}

package com.japaneselearning.japanese_learning_api.user;

import com.japaneselearning.japanese_learning_api.auth.AuthDtos;
import com.japaneselearning.japanese_learning_api.auth.AuthService;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:user-settings-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
class UserSettingsIntegrationTest {
    @Autowired AuthService auth;
    @Autowired UserSettingsController controller;
    @Autowired Validator validator;

    @Test void settingsHaveSafeDefaultsAndAcceptSupportedValues() {
        var account = auth.register(new AuthDtos.RegisterRequest("settings@example.com", "TestPassword123!", "Settings", "test"));
        var principal = new TestingAuthenticationToken(account.user().id().toString(), "", "ROLE_USER");
        var defaults = controller.get(principal);
        assertThat(defaults.srsAgainMinutes()).isEqualTo(1);
        assertThat(defaults.srsDailyNewLimit()).isEqualTo(20);
        assertThat(defaults.srsGoodMinutes()).isEqualTo(10);
        assertThat(defaults.srsDesiredRetention()).isEqualByComparingTo("0.900");

        var changed = controller.update(principal, request(5000, 2, 15, "0.930"));
        assertThat(changed.srsDailyNewLimit()).isEqualTo(5000);
        assertThat(changed.srsAgainMinutes()).isEqualTo(2);
        assertThat(changed.srsGoodMinutes()).isEqualTo(15);
        assertThat(changed.srsDesiredRetention()).isEqualByComparingTo("0.930");
    }

    @Test void invalidRangesAndOrderingAreRejected() {
        assertThat(validator.validate(request(20, 0, 10, "0.900"))).isNotEmpty();
        assertThat(validator.validate(request(-1, 1, 10, "0.900"))).isNotEmpty();
        var account = auth.register(new AuthDtos.RegisterRequest("invalid-settings@example.com", "TestPassword123!", "Settings", "test"));
        var principal = new TestingAuthenticationToken(account.user().id().toString(), "", "ROLE_USER");
        assertThatThrownBy(() -> controller.update(principal, request(20, 10, 10, "0.900"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.update(principal, request(20, 1, 10, "0.990"))).isInstanceOf(ApiException.class);
    }

    private UserSettingsDtos.UpdateRequest request(int dailyNewLimit, int again, int good, String retention) {
        return new UserSettingsDtos.UpdateRequest(null, null, null, null, null, null, null, null,
                dailyNewLimit, again, good, new BigDecimal(retention), null);
    }
}

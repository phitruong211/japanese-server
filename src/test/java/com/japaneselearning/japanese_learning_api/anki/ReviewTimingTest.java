package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewTimingTest {
    @Test void legacyRequestHasNoInventedTiming() {
        assertThat(ReviewTiming.isValid(new AnkiDtos.ReviewRequest(DomainTypes.AnkiRating.GOOD, 1000))).isFalse();
    }
    @Test void activeTimeCanBeShorterThanWallTime() {
        assertThat(ReviewTiming.isValid(request(5000, 1000))).isTrue();
    }
    @Test void zeroNegativeOrImpossibleDurationsAreInvalid() {
        assertThat(ReviewTiming.isValid(request(0, 1000))).isFalse();
        assertThat(ReviewTiming.isValid(request(-1, 1000))).isFalse();
        assertThat(ReviewTiming.isValid(request(11000, 1000))).isFalse();
    }
    private AnkiDtos.ReviewRequest request(int recall, int decision) {
        return new AnkiDtos.ReviewRequest(null, DomainTypes.AnkiRating.HARD, null, recall, decision, true,
                Instant.parse("2026-10-15T07:00:00+07:00"), Instant.parse("2026-10-15T00:00:10Z"),
                Instant.parse("2026-10-15T00:00:12Z"), 0);
    }
}

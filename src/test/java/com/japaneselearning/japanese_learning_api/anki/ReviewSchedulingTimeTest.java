package com.japaneselearning.japanese_learning_api.anki;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
class ReviewSchedulingTimeTest {
 @Test void retainsOriginalRatingTimeForDelayedDelivery() {
  Instant now=Instant.parse("2026-10-15T10:00:00Z");
  assertThat(ReviewSchedulingTime.resolve(now.minusSeconds(3600),now.minusSeconds(7200),now)).isEqualTo(now.minusSeconds(3600));
 }
 @Test void fallsBackForMissingFutureOrOutOfOrderTime() {
  Instant now=Instant.parse("2026-10-15T10:00:00Z");
  assertThat(ReviewSchedulingTime.resolve(null,null,now)).isEqualTo(now);
  assertThat(ReviewSchedulingTime.resolve(now.plusSeconds(60),null,now)).isEqualTo(now);
  assertThat(ReviewSchedulingTime.resolve(now.minusSeconds(10),now.minusSeconds(5),now)).isEqualTo(now);
 }
}

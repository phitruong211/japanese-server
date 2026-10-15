package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import com.japaneselearning.japanese_learning_api.model.UserSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AnkiSchedulerTest {
    private final AnkiScheduler scheduler = new AnkiScheduler();
    private final Instant now = Instant.parse("2026-10-10T00:00:00Z");

    @Test void newCardAgainStartsConfiguredLearningStep() {
        AnkiCardProgress progress = fresh();
        scheduler.apply(progress, DomainTypes.AnkiRating.AGAIN, now, 1000, settings(2, 15), false);
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.LEARNING);
        assertThat(progress.getIntervalMinutes()).isEqualTo(2);
        assertThat(progress.getDueAt()).isEqualTo(now.plusSeconds(120));
        assertThat(progress.getLastRating()).isEqualTo(DomainTypes.AnkiRating.AGAIN);
        assertThat(progress.getFsrsStability()).isPositive();
        assertThat(progress.getFsrsDifficulty()).isBetween(1.0, 10.0);
    }

    @Test void goodUsesConfiguredSecondLearningStep() {
        AnkiCardProgress progress = fresh();
        scheduler.apply(progress, DomainTypes.AnkiRating.GOOD, now, 1000, settings(1, 15), false);
        assertThat(progress.getDueAt()).isEqualTo(now.plusSeconds(15 * 60));
        assertThat(progress.getRepetitions()).isEqualTo(1);
    }

    @Test void reviewAgainCreatesRelearningLapseWithoutLosingReviewCount() {
        AnkiCardProgress progress = fresh();
        progress.setState(DomainTypes.AnkiState.REVIEW);
        progress.setIntervalDays(10);
        progress.setRepetitions(3);
        progress.setLastReviewedAt(now.minusSeconds(10 * 86400));
        scheduler.apply(progress, DomainTypes.AnkiRating.AGAIN, now, 1000, settings(1, 10), false);
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.RELEARNING);
        assertThat(progress.getLapses()).isEqualTo(1);
        assertThat(progress.getRepetitions()).isEqualTo(4);
        assertThat(progress.getDueAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test void everyRatingWorksAndResponseDurationCannotChangeSchedule() {
        for (var rating : DomainTypes.AnkiRating.values()) {
            var fast = fresh(); var slow = fresh();
            scheduler.apply(fast, rating, now, 0, settings(1, 10), false);
            scheduler.apply(slow, rating, now, 999999, settings(1, 10), false);
            assertThat(fast.getDueAt()).isEqualTo(slow.getDueAt()).isAfter(now);
            assertThat(fast.getFsrsStability()).isEqualTo(slow.getFsrsStability());
            assertThat(fast.getLapses()).isZero();
        }
    }

    @Test void retrievabilityUsesPreReviewHistoryAndHardIsSuccessfulRecall() {
        var progress = fresh();
        assertThat(scheduler.predictedRecall(progress, now)).isNull();
        scheduler.apply(progress, DomainTypes.AnkiRating.EASY, now, null, settings(1, 10), false);
        double early = scheduler.predictedRecall(progress, now.plusSeconds(86400));
        double late = scheduler.predictedRecall(progress, now.plusSeconds(30 * 86400));
        assertThat(early).isGreaterThan(late);
        scheduler.apply(progress, DomainTypes.AnkiRating.HARD, now.plusSeconds(30 * 86400), null, settings(1, 10), false);
        assertThat(progress.getLapses()).isZero();
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.REVIEW);
    }

    @Test void repeatedForgettingRetainsHistoryAndDifferentHistoriesProduceDifferentMemory() {
        var forgotten = fresh(); var remembered = fresh();
        scheduler.apply(forgotten, DomainTypes.AnkiRating.EASY, now, null, settings(1, 10), false);
        scheduler.apply(remembered, DomainTypes.AnkiRating.EASY, now, null, settings(1, 10), false);
        for (int day = 1; day <= 3; day++) {
            Instant review = now.plusSeconds(day * 86400L);
            scheduler.apply(forgotten, DomainTypes.AnkiRating.AGAIN, review, null, settings(1, 10), false);
            scheduler.apply(forgotten, DomainTypes.AnkiRating.GOOD, review.plusSeconds(60), null, settings(1, 10), false);
            scheduler.apply(remembered, DomainTypes.AnkiRating.GOOD, review, null, settings(1, 10), false);
        }
        assertThat(forgotten.getLapses()).isEqualTo(3);
        assertThat(forgotten.getRepetitions()).isEqualTo(7);
        assertThat(remembered.getFsrsStability()).isGreaterThan(forgotten.getFsrsStability());
    }

    @Test void legacyLearningCardWithoutMemoryFieldsCanResume() {
        var progress = fresh();
        progress.setState(DomainTypes.AnkiState.LEARNING);
        progress.setLastReviewedAt(now.minusSeconds(2 * 86400));
        scheduler.apply(progress, DomainTypes.AnkiRating.GOOD, now, null, settings(1, 10), false);
        assertThat(progress.getFsrsStability()).isPositive();
        assertThat(progress.getDueAt()).isAfter(now);
    }

    private AnkiCardProgress fresh() {
        AnkiCardProgress progress = new AnkiCardProgress();
        progress.setState(DomainTypes.AnkiState.NEW);
        progress.setEaseFactor(new BigDecimal("2.50"));
        progress.setDueAt(now);
        return progress;
    }

    private UserSettings settings(int again, int good) {
        UserSettings settings = new UserSettings();
        settings.setSrsAgainMinutes(again);
        settings.setSrsGoodMinutes(good);
        settings.setSrsDesiredRetention(new BigDecimal("0.900"));
        return settings;
    }
}

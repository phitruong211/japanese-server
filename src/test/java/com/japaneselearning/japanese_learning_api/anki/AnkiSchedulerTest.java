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

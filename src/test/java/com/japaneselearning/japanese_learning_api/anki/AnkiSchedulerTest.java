package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AnkiSchedulerTest {
    private final AnkiScheduler scheduler = new AnkiScheduler();
    private final Instant now = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void newCardAgainStartsOneMinuteLearningStep() {
        AnkiCardProgress progress = fresh();
        scheduler.apply(progress, DomainTypes.AnkiRating.AGAIN, now);
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.LEARNING);
        assertThat(progress.getIntervalMinutes()).isEqualTo(1);
        assertThat(progress.getDueAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void easyGraduatesNewCardToFourDayReview() {
        AnkiCardProgress progress = fresh();
        scheduler.apply(progress, DomainTypes.AnkiRating.EASY, now);
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.REVIEW);
        assertThat(progress.getIntervalDays()).isEqualTo(4);
        assertThat(progress.getEaseFactor()).isEqualByComparingTo("2.65");
    }

    @Test
    void reviewAgainCreatesRelearningLapse() {
        AnkiCardProgress progress = fresh();
        progress.setState(DomainTypes.AnkiState.REVIEW);
        progress.setIntervalDays(10);
        progress.setRepetitions(3);
        scheduler.apply(progress, DomainTypes.AnkiRating.AGAIN, now);
        assertThat(progress.getState()).isEqualTo(DomainTypes.AnkiState.RELEARNING);
        assertThat(progress.getLapses()).isEqualTo(1);
        assertThat(progress.getEaseFactor()).isEqualByComparingTo("2.30");
    }

    private AnkiCardProgress fresh() {
        AnkiCardProgress progress = new AnkiCardProgress();
        progress.setState(DomainTypes.AnkiState.NEW);
        progress.setEaseFactor(new BigDecimal("2.50"));
        progress.setDueAt(now);
        return progress;
    }
}

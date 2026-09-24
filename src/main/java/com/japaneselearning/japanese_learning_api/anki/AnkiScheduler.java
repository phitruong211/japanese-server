package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class AnkiScheduler {
    private static final List<Integer> LEARNING_STEPS = List.of(1, 10, 60);
    private static final BigDecimal MIN_EASE = new BigDecimal("1.30");
    private static final BigDecimal HARD_PENALTY = new BigDecimal("0.15");
    private static final BigDecimal AGAIN_PENALTY = new BigDecimal("0.20");
    private static final BigDecimal EASY_BONUS = new BigDecimal("0.15");

    public void apply(AnkiCardProgress progress, DomainTypes.AnkiRating rating, Instant now) {
        progress.setLastReviewedAt(now);
        if (progress.getState() == DomainTypes.AnkiState.REVIEW) applyReview(progress, rating, now);
        else applyLearning(progress, rating, now);
    }

    private void applyLearning(AnkiCardProgress progress, DomainTypes.AnkiRating rating, Instant now) {
        int currentStep = progress.getIntervalMinutes() == null
                ? 0
                : Math.max(0, LEARNING_STEPS.indexOf(progress.getIntervalMinutes()));
        switch (rating) {
            case AGAIN -> {
                progress.setIntervalMinutes(1);
                progress.setDueAt(now.plus(1, ChronoUnit.MINUTES));
                progress.setState(progress.getState() == DomainTypes.AnkiState.RELEARNING
                        ? DomainTypes.AnkiState.RELEARNING : DomainTypes.AnkiState.LEARNING);
            }
            case HARD -> {
                int minutes = LEARNING_STEPS.get(currentStep);
                progress.setIntervalMinutes(minutes);
                progress.setDueAt(now.plus(minutes, ChronoUnit.MINUTES));
                progress.setState(progress.getState() == DomainTypes.AnkiState.RELEARNING
                        ? DomainTypes.AnkiState.RELEARNING : DomainTypes.AnkiState.LEARNING);
            }
            case GOOD -> {
                if (currentStep + 1 >= LEARNING_STEPS.size()) graduate(progress, 1, now);
                else {
                    int minutes = LEARNING_STEPS.get(currentStep + 1);
                    progress.setIntervalMinutes(minutes);
                    progress.setDueAt(now.plus(minutes, ChronoUnit.MINUTES));
                    progress.setState(progress.getState() == DomainTypes.AnkiState.RELEARNING
                            ? DomainTypes.AnkiState.RELEARNING : DomainTypes.AnkiState.LEARNING);
                }
            }
            case EASY -> {
                progress.setEaseFactor(maxEase(progress.getEaseFactor().add(EASY_BONUS)));
                graduate(progress, 4, now);
            }
        }
    }

    private void applyReview(AnkiCardProgress progress, DomainTypes.AnkiRating rating, Instant now) {
        int currentInterval = progress.getIntervalDays() == null ? 1 : progress.getIntervalDays();
        switch (rating) {
            case AGAIN -> {
                progress.setState(DomainTypes.AnkiState.RELEARNING);
                progress.setLapses(progress.getLapses() + 1);
                progress.setEaseFactor(maxEase(progress.getEaseFactor().subtract(AGAIN_PENALTY)));
                progress.setIntervalMinutes(1);
                progress.setIntervalDays(null);
                progress.setRepetitions(0);
                progress.setDueAt(now.plus(1, ChronoUnit.MINUTES));
            }
            case HARD -> scheduleDays(progress, Math.max(1, Math.round(currentInterval * 1.2f)),
                    maxEase(progress.getEaseFactor().subtract(HARD_PENALTY)), now);
            case GOOD -> scheduleDays(progress,
                    Math.max(1, Math.round(currentInterval * progress.getEaseFactor().floatValue())),
                    progress.getEaseFactor(), now);
            case EASY -> {
                BigDecimal ease = maxEase(progress.getEaseFactor().add(EASY_BONUS));
                scheduleDays(progress, Math.max(1, Math.round(currentInterval * ease.floatValue() * 1.3f)), ease, now);
            }
        }
    }

    private void graduate(AnkiCardProgress progress, int days, Instant now) {
        progress.setState(DomainTypes.AnkiState.REVIEW);
        progress.setIntervalDays(days);
        progress.setIntervalMinutes(null);
        progress.setRepetitions(1);
        progress.setDueAt(now.plus(days, ChronoUnit.DAYS));
    }

    private void scheduleDays(AnkiCardProgress progress, int days, BigDecimal ease, Instant now) {
        progress.setEaseFactor(ease);
        progress.setIntervalDays(days);
        progress.setRepetitions(progress.getRepetitions() + 1);
        progress.setDueAt(now.plus(days, ChronoUnit.DAYS));
    }

    private BigDecimal maxEase(BigDecimal value) { return value.max(MIN_EASE); }
}

package com.japaneselearning.japanese_learning_api.learning;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Locale;
import static com.japaneselearning.japanese_learning_api.learning.LearningDtos.SrsCard;

final class BuiltInSchedulerAdapter {
    private BuiltInSchedulerAdapter() {}
    static AnkiCardProgress read(SrsCard card, Instant now) {
        var progress = new AnkiCardProgress();
        progress.setDueAt(now);
        if (card == null) return progress;
        progress.setState(DomainTypes.AnkiState.valueOf(card.state().toUpperCase(Locale.ROOT)));
        progress.setEaseFactor(BigDecimal.valueOf(card.easeFactor()));
        progress.setIntervalDays(card.intervalDays() == null ? (card.intervalMinutes() == null ? null
                : Math.max(1, (int)(card.intervalMinutes() / 1440))) : card.intervalDays().intValue());
        progress.setDueAt(Instant.parse(card.dueDate()));
        progress.setRepetitions(card.reps()); progress.setLapses(card.lapses());
        progress.setLastReviewedAt(card.lastReviewedAt() == null ? null : Instant.parse(card.lastReviewedAt()));
        progress.setFirstReviewedAt(card.firstReviewedAt() == null ? null : Instant.parse(card.firstReviewedAt()));
        progress.setFsrsStability(card.stability()); progress.setFsrsDifficulty(card.difficulty());
        progress.setFsrsStep(card.learningSteps());
        return progress;
    }
    static SrsCard write(String id, String type, AnkiCardProgress p) {
        return new SrsCard(id, type, p.getState().name().toLowerCase(Locale.ROOT), p.getEaseFactor().doubleValue(),
                p.getIntervalMinutes() == null ? null : p.getIntervalMinutes().doubleValue(),
                p.getIntervalDays() == null ? null : p.getIntervalDays().doubleValue(), p.getDueAt().toString(),
                p.getRepetitions(), p.getLapses(), p.getLastReviewedAt().toString(), p.getFsrsAlgorithm(),
                p.getFsrsStability(), p.getFsrsDifficulty(), p.getFsrsElapsedDays(), p.getFsrsScheduledDays(),
                p.getFsrsStep() == null ? 0 : p.getFsrsStep(), p.getFirstReviewedAt().toString(),
                p.getLastRating().name().toLowerCase(Locale.ROOT));
    }
}

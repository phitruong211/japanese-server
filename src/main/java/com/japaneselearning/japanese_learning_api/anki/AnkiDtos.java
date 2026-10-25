package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AnkiDtos {
    private AnkiDtos() {}

    public record ReviewRequest(UUID clientReviewId, @NotNull DomainTypes.AnkiRating rating,
                                @PositiveOrZero Integer responseTimeMs,
                                Integer recallTimeMs, Integer decisionTimeMs, Boolean isTimeValid,
                                Instant shownAt, Instant revealedAt, Instant ratedAt,
                                Integer expectedRepetitions) {
        public ReviewRequest(UUID clientReviewId, DomainTypes.AnkiRating rating, Integer responseTimeMs) {
            this(clientReviewId, rating, responseTimeMs, null, null, null, null, null, null, null);
        }
        public ReviewRequest(DomainTypes.AnkiRating rating, Integer responseTimeMs) {
            this(null, rating, responseTimeMs);
        }
    }

    public record ProgressResponse(DomainTypes.AnkiState state, BigDecimal easeFactor,
                                   Integer intervalMinutes, Integer intervalDays, Instant dueAt,
                                   int repetitions, int lapses, Instant lastReviewedAt,
                                   String algorithm, Double stability, Double difficulty,
                                   Integer elapsedDays, Integer scheduledDays, Integer learningSteps,
                                   Instant firstReviewedAt, DomainTypes.AnkiRating lastRating) {}

    public record QueueCard(UUID cardId, UUID deckId, String deckName, String front, String back,
                            String reading, String notes, DomainTypes.CardKind kind,
                            Map<String, Object> extraData,
                            ProgressResponse progress, String noteType, Map<String, Object> fields) {}

    public record UndoResponse(UUID cardId, ProgressResponse progress) {}

    public record ReviewResponse(UUID cardId, DomainTypes.AnkiRating rating, ProgressResponse progress) {}
}

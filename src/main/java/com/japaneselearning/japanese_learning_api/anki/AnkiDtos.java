package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AnkiDtos {
    private AnkiDtos() {}

    public record ReviewRequest(@NotNull DomainTypes.AnkiRating rating,
                                @PositiveOrZero Integer responseTimeMs) {}

    public record ProgressResponse(DomainTypes.AnkiState state, BigDecimal easeFactor,
                                   Integer intervalMinutes, Integer intervalDays, Instant dueAt,
                                   int repetitions, int lapses, Instant lastReviewedAt) {}

    public record QueueCard(UUID cardId, UUID deckId, String deckName, String front, String back,
                            String reading, String notes, DomainTypes.CardKind kind,
                            ProgressResponse progress) {}

    public record ReviewResponse(UUID cardId, DomainTypes.AnkiRating rating, ProgressResponse progress) {}
}

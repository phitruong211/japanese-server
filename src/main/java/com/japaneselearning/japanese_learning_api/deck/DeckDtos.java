package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeckDtos {
    private DeckDtos() {}

    public record CreateDeckRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            DomainTypes.SourceType sourceType,
            @Size(max = 255) String sourceName,
            @Size(max = 20) String importFormat,
            DomainTypes.Visibility visibility,
            Map<String, Object> templateConfig,
            @Size(max = 20_000) List<@NotNull @Valid CardRequest> cards,
            @Pattern(regexp = "MANUAL|IMPORT|BUILT_IN") String source,
            @Size(max = 1000) String sourceRef, @Size(max = 255) String sourceSheet,
            @Size(max = 100) List<@NotBlank @Size(max = 100) String> tags) {}

    public record UpdateDeckRequest(
            @Size(min = 1, max = 200) String name,
            @Size(max = 5000) String description,
            DomainTypes.Visibility visibility,
            Map<String, Object> templateConfig) {}

    public record ReorderDecksRequest(@NotEmpty List<@NotNull UUID> deckIds) {}
    public record ReorderCardsRequest(@NotEmpty List<@NotNull UUID> cardIds) {}
    public record MoveCardsRequest(@NotEmpty List<@NotNull UUID> cardIds, @NotNull UUID targetDeckId,
                                   @PositiveOrZero Integer targetPosition) {}

    public record CardRequest(
            @NotBlank @Size(max = 20_000) String front,
            @NotBlank @Size(max = 20_000) String back,
            @Size(max = 10_000) String reading,
            @Size(max = 20_000) String notes,
            DomainTypes.CardKind kind,
            @PositiveOrZero Integer position,
            @Size(max = 255) String externalId,
            Map<String, Object> extraData,
            @Size(max = 100) List<@NotBlank @Size(max = 100) String> tags) {}

    public record UpdateCardRequest(
            @Size(min = 1, max = 20_000) String front,
            @Size(min = 1, max = 20_000) String back,
            @Size(max = 10_000) String reading,
            @Size(max = 20_000) String notes,
            DomainTypes.CardKind kind,
            @PositiveOrZero Integer position,
            Map<String, Object> extraData,
            @Size(max = 100) List<@NotBlank @Size(max = 100) String> tags) {}

    public record DeckSummary(UUID id, String name, String description, DomainTypes.SourceType sourceType,
                              String sourceName, String importFormat, DomainTypes.Visibility visibility,
                              int cardCount, int position, Map<String, Object> templateConfig,
                              Instant createdAt, Instant updatedAt, String source, String sourceRef, String sourceSheet,
                              List<String> tags, long newCount, long dueCount) {}
    public record CardResponse(UUID id, UUID deckId, String front, String back, String reading, String notes,
                               DomainTypes.CardKind kind, int position, String externalId,
                               Map<String, Object> extraData, Instant createdAt, Instant updatedAt, List<String> tags,
                               ProgressResponse progress, String source, String sourceRef, String sourceSheet) {}
    public record ProgressResponse(DomainTypes.AnkiState state, Instant dueAt, Instant lastReviewedAt,
                                   int repetitions, int lapses, java.math.BigDecimal easeFactor, Integer intervalMinutes, Integer intervalDays) {}
    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
        }
    }
    public record DeckResponse(DeckSummary deck, List<CardResponse> cards) {}
}

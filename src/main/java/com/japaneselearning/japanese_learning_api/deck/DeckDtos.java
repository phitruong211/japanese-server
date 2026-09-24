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
            @Size(max = 20_000) List<@Valid CardRequest> cards) {}

    public record UpdateDeckRequest(
            @Size(min = 1, max = 200) String name,
            @Size(max = 5000) String description,
            DomainTypes.Visibility visibility) {}

    public record CardRequest(
            @NotBlank @Size(max = 20_000) String front,
            @NotBlank @Size(max = 20_000) String back,
            @Size(max = 10_000) String reading,
            @Size(max = 20_000) String notes,
            DomainTypes.CardKind kind,
            @PositiveOrZero Integer position,
            @Size(max = 255) String externalId,
            Map<String, Object> extraData) {}

    public record UpdateCardRequest(
            @Size(min = 1, max = 20_000) String front,
            @Size(min = 1, max = 20_000) String back,
            @Size(max = 10_000) String reading,
            @Size(max = 20_000) String notes,
            DomainTypes.CardKind kind,
            @PositiveOrZero Integer position,
            Map<String, Object> extraData) {}

    public record DeckSummary(UUID id, String name, String description, DomainTypes.SourceType sourceType,
                              String sourceName, String importFormat, DomainTypes.Visibility visibility,
                              int cardCount, Instant createdAt, Instant updatedAt) {}
    public record CardResponse(UUID id, UUID deckId, String front, String back, String reading, String notes,
                               DomainTypes.CardKind kind, int position, String externalId,
                               Map<String, Object> extraData, Instant createdAt, Instant updatedAt) {}
    public record DeckResponse(DeckSummary deck, List<CardResponse> cards) {}
}

package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeckController {
    private final DeckService service;
    public DeckController(DeckService service) { this.service = service; }

    @GetMapping("/decks")
    Object list(Authentication auth, @RequestParam(required = false) Integer page,
                @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return page == null ? service.list(CurrentUser.id(auth)) : service.listPage(CurrentUser.id(auth), page, size, sort);
    }

    @GetMapping("/decks/{deckId}/metadata")
    DeckDtos.DeckSummary metadata(Authentication auth, @PathVariable UUID deckId) { return service.metadata(CurrentUser.id(auth), deckId); }

    @GetMapping("/decks/{deckId}/card-ids")
    List<UUID> cardIds(Authentication auth, @PathVariable UUID deckId) { return service.cardIds(CurrentUser.id(auth), deckId); }

    @GetMapping("/decks/{deckId}/cards")
    DeckDtos.PageResponse<DeckDtos.CardResponse> cards(Authentication auth, @PathVariable UUID deckId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String query, @RequestParam(required = false) String type,
            @RequestParam(required = false) String state) {
        return service.cardPage(CurrentUser.id(auth), deckId, page, size, query, type, state);
    }

    @PostMapping("/decks/import") @ResponseStatus(HttpStatus.CREATED)
    DeckDtos.DeckResponse importDeck(Authentication auth, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DeckDtos.CreateDeckRequest request) {
        return service.importDeck(CurrentUser.id(auth), key, request);
    }

    @GetMapping("/decks/{deckId}")
    DeckDtos.DeckResponse get(Authentication auth, @PathVariable UUID deckId) {
        return service.get(CurrentUser.id(auth), deckId);
    }

    @PostMapping("/decks") @ResponseStatus(HttpStatus.CREATED)
    DeckDtos.DeckResponse create(Authentication auth, @Valid @RequestBody DeckDtos.CreateDeckRequest request) {
        return service.create(CurrentUser.id(auth), request);
    }

    @PatchMapping("/decks/{deckId}")
    DeckDtos.DeckSummary update(Authentication auth, @PathVariable UUID deckId,
                                @Valid @RequestBody DeckDtos.UpdateDeckRequest request) {
        return service.update(CurrentUser.id(auth), deckId, request);
    }

    @PutMapping("/decks/reorder")
    List<DeckDtos.DeckSummary> reorderDecks(Authentication auth,
                                             @Valid @RequestBody DeckDtos.ReorderDecksRequest request) {
        return service.reorderDecks(CurrentUser.id(auth), request);
    }

    @DeleteMapping("/decks/{deckId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, @PathVariable UUID deckId) { service.delete(CurrentUser.id(auth), deckId); }

    @PostMapping("/decks/{deckId}/cards") @ResponseStatus(HttpStatus.CREATED)
    DeckDtos.CardResponse addCard(Authentication auth, @PathVariable UUID deckId,
                                  @Valid @RequestBody DeckDtos.CardRequest request) {
        return service.addCard(CurrentUser.id(auth), deckId, request);
    }

    @PatchMapping("/cards/{cardId}")
    DeckDtos.CardResponse updateCard(Authentication auth, @PathVariable UUID cardId,
                                     @Valid @RequestBody DeckDtos.UpdateCardRequest request) {
        return service.updateCard(CurrentUser.id(auth), cardId, request);
    }

    @PutMapping("/decks/{deckId}/cards/reorder")
    List<DeckDtos.CardResponse> reorderCards(Authentication auth, @PathVariable UUID deckId,
                                              @Valid @RequestBody DeckDtos.ReorderCardsRequest request) {
        return service.reorderCards(CurrentUser.id(auth), deckId, request);
    }

    @PostMapping("/cards/move")
    List<DeckDtos.CardResponse> moveCards(Authentication auth,
                                           @Valid @RequestBody DeckDtos.MoveCardsRequest request) {
        return service.moveCards(CurrentUser.id(auth), request);
    }

    @DeleteMapping("/cards/{cardId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCard(Authentication auth, @PathVariable UUID cardId) { service.deleteCard(CurrentUser.id(auth), cardId); }
}

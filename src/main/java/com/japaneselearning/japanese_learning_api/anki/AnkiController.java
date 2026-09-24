package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/anki")
public class AnkiController {
    private final AnkiService service;
    public AnkiController(AnkiService service) { this.service = service; }

    @GetMapping("/due")
    List<AnkiDtos.QueueCard> due(Authentication auth,
                                 @RequestParam(required = false) UUID deckId,
                                 @RequestParam(defaultValue = "50") int limit) {
        return service.queue(CurrentUser.id(auth), deckId, limit);
    }

    @GetMapping("/decks/{deckId}/cards")
    List<AnkiDtos.QueueCard> deckCards(Authentication auth, @PathVariable UUID deckId) {
        return service.deckCards(CurrentUser.id(auth), deckId);
    }

    @PostMapping("/cards/{cardId}/reviews")
    AnkiDtos.ReviewResponse review(Authentication auth, @PathVariable UUID cardId,
                                   @Valid @RequestBody AnkiDtos.ReviewRequest request) {
        return service.review(CurrentUser.id(auth), cardId, request);
    }
}

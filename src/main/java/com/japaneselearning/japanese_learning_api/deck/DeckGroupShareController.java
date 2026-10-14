package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeckGroupShareController {
    private final DeckGroupShareService service;
    public DeckGroupShareController(DeckGroupShareService service) { this.service = service; }
    @GetMapping("/deck-groups/{id}/share")
    ResponseEntity<DeckGroupShareService.Link> status(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(CurrentUser.id(auth), id));
    }
    @PutMapping("/deck-groups/{id}/share")
    DeckGroupShareService.Link enable(Authentication auth, @PathVariable UUID id, @RequestBody(required = false) DeckShareService.ShareOptions options) { return service.enable(CurrentUser.id(auth), id, options); }
    @DeleteMapping("/deck-groups/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(Authentication auth, @PathVariable UUID id) { service.revoke(CurrentUser.id(auth), id); }
    @GetMapping("/shared-deck-groups/{token}")
    ResponseEntity<DeckGroupShareService.Preview> preview(@PathVariable String token, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow")
                .body(service.preview(token, page));
    }
    @GetMapping("/shared-deck-groups/{token}/decks/{deckId}")
    ResponseEntity<DeckShareService.Preview> previewDeck(@PathVariable String token, @PathVariable UUID deckId,
                                                       @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow")
                .body(service.previewDeck(token, deckId, page));
    }
    @PostMapping("/shared-deck-groups/{token}/copy")
    DeckGroupShareService.CopyResult copy(Authentication auth, @PathVariable String token) {
        return service.copy(CurrentUser.id(auth), token);
    }
}

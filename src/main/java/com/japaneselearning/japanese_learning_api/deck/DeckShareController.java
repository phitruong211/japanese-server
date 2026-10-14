package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeckShareController {
    private final DeckShareService service;
    public DeckShareController(DeckShareService service) { this.service = service; }

    @GetMapping("/decks/{id}/share")
    ResponseEntity<DeckShareService.Link> status(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(CurrentUser.id(auth), id));
    }
    @PutMapping("/decks/{id}/share")
    DeckShareService.Link enable(Authentication auth, @PathVariable UUID id, @RequestBody(required = false) DeckShareService.ShareOptions options) { return service.enable(CurrentUser.id(auth), id, options); }

    @DeleteMapping("/decks/{id}/share")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void revoke(Authentication auth, @PathVariable UUID id) { service.revoke(CurrentUser.id(auth), id); }

    @GetMapping("/shared-decks/{token}")
    ResponseEntity<DeckShareService.Preview> preview(@PathVariable String token, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow").body(service.preview(token, page));
    }
    @PostMapping("/shared-decks/{token}/copy")
    DeckShareService.CopyResult copy(Authentication auth, @PathVariable String token) { return service.copy(CurrentUser.id(auth), token); }
}

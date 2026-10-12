package com.japaneselearning.japanese_learning_api.deck;
import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class DeckGroupController {
    private final DeckGroupService service;
    public DeckGroupController(DeckGroupService service) { this.service = service; }
    @GetMapping("/deck-groups") List<DeckGroupService.Summary> list(Authentication auth) { return service.list(CurrentUser.id(auth)); }
    @PostMapping("/deck-groups") @ResponseStatus(HttpStatus.NO_CONTENT)
    void create(Authentication auth, @Valid @RequestBody DeckGroupService.NameRequest request) { service.create(CurrentUser.id(auth), request); }
    @PatchMapping("/deck-groups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void rename(Authentication auth, @PathVariable UUID id, @Valid @RequestBody DeckGroupService.NameRequest request) { service.rename(CurrentUser.id(auth), id, request); }
    @DeleteMapping("/deck-groups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void dissolve(Authentication auth, @PathVariable UUID id, @RequestParam long version) { service.dissolve(CurrentUser.id(auth), id, version); }
    @PutMapping("/deck-groups/order") @ResponseStatus(HttpStatus.NO_CONTENT)
    void order(Authentication auth, @Valid @RequestBody DeckGroupService.OrderRequest request) { service.reorder(CurrentUser.id(auth), request); }
    @PutMapping("/decks/{id}/group") @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(Authentication auth, @PathVariable UUID id, @Valid @RequestBody DeckGroupService.MoveRequest request) { service.move(CurrentUser.id(auth), id, request); }
}

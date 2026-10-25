package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.common.RateLimiter;
import com.japaneselearning.japanese_learning_api.repository.DeckGroupRepository;
import com.japaneselearning.japanese_learning_api.repository.DeckRepository;
import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/** Share-code lookup, the public library listing, publishing, reporting and moderation. Lookups and browsing are public and rate limited. */
@RestController
@RequestMapping("/api/v1")
public class ShareCodeController {
    public record Resolved(String kind, String token) {}
    public record RemoveRequest(String reason) {}
    private final DeckRepository decks;
    private final DeckGroupRepository groups;
    private final LibraryService library;
    private final RateLimiter limiter;

    public ShareCodeController(DeckRepository decks, DeckGroupRepository groups, LibraryService library, RateLimiter limiter) {
        this.decks = decks; this.groups = groups; this.library = library; this.limiter = limiter;
    }

    @GetMapping("/share-codes/{code}")
    @Transactional(readOnly = true)
    ResponseEntity<Resolved> resolve(@PathVariable String code, HttpServletRequest request) {
        // ~40 bits of code: cap guesses per client so enumeration is not practical.
        limiter.check("share-code", limiter.client(request), 20, Duration.ofMinutes(1), "Bạn thử quá nhiều lần. Hãy đợi một lát rồi nhập lại.");
        String normalized = ShareCodes.normalize(code);
        Resolved found = null;
        if (normalized != null) {
            var deck = decks.findByShareCodeAndDeletedAtIsNull(normalized).filter(d -> d.getShareToken() != null && !ShareCodes.expired(d.getShareExpiresAt()));
            if (deck.isPresent()) found = new Resolved("deck", deck.get().getShareToken());
            else {
                var group = groups.findByShareCode(normalized).filter(g -> g.getShareToken() != null && !ShareCodes.expired(g.getShareExpiresAt()));
                if (group.isPresent()) found = new Resolved("group", group.get().getShareToken());
            }
        }
        if (found == null) throw ApiException.notFound("Mã không đúng hoặc đã hết hạn.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow").body(found);
    }

    @GetMapping("/library")
    ResponseEntity<LibraryService.Page> browse(@RequestParam(required = false) String query, @RequestParam(required = false) String level,
                                               @RequestParam(defaultValue = "recent") String sort, @RequestParam(defaultValue = "0") int page, HttpServletRequest request) {
        limiter.check("library", limiter.client(request), 120, Duration.ofMinutes(1), "Bạn tải trang quá nhanh. Hãy đợi một lát.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.list(query, level, sort, page));
    }

    @PutMapping("/decks/{id}/library")
    DeckShareService.Link publish(Authentication auth, @PathVariable UUID id, @RequestBody LibraryService.PublishRequest body) {
        return library.publish(CurrentUser.id(auth), id, body);
    }

    @PostMapping("/shared-decks/{token}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(Authentication auth, @PathVariable String token, @RequestBody LibraryService.ReportRequest body) {
        library.report(CurrentUser.id(auth), token, body);
    }

    @GetMapping("/admin/reports")
    LibraryService.ReportPage reports(Authentication auth, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
        return library.listReports(CurrentUser.id(auth), status, page);
    }

    @PostMapping("/admin/reports/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(Authentication auth, @PathVariable UUID id) { library.dismiss(CurrentUser.id(auth), id); }

    @PostMapping("/admin/decks/{id}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Authentication auth, @PathVariable UUID id, @RequestBody RemoveRequest body) { library.remove(CurrentUser.id(auth), id, body.reason()); }

    @PostMapping("/admin/decks/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(Authentication auth, @PathVariable UUID id) { library.restore(CurrentUser.id(auth), id); }
}

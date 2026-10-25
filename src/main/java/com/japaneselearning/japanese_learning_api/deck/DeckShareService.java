package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DeckShareService {
    public record Library(boolean published, String level, String moderation, String removedReason, int downloads) {}
    /** `code` is the short code to type or scan ("K7QM-3XPD"); `expiresAt` is null when the link never expires. */
    public record Link(String token, boolean includePersonalNotes, String code, Instant expiresAt, Library library) {}
    /** `expiresInDays` is 1, 7, 30 or null (never). Published decks never expire. */
    public record ShareOptions(boolean includePersonalNotes, Integer expiresInDays, Boolean keepExpiry) {
        public ShareOptions(boolean includePersonalNotes) { this(includePersonalNotes, null, null); }
        public ShareOptions(boolean includePersonalNotes, Integer expiresInDays) { this(includePersonalNotes, expiresInDays, null); }
        boolean keepsExpiry() { return Boolean.TRUE.equals(keepExpiry); }
    }
    public record PreviewCard(String front, String back, String reading, DomainTypes.CardKind kind, boolean dictionarySource) {}
    public record Preview(String name, String description, String author, int cardCount,
                          int previewCount, int page, int size, int totalPages, List<PreviewCard> cards) {}
    public record CopyResult(UUID deckId, boolean alreadySaved) {}

    private final DeckRepository decks;
    private final CardRepository cards;
    private final SharedDeckContent content;
    private final DeckGroupRepository groups;
    @PersistenceContext private EntityManager em;

    public DeckShareService(DeckRepository decks, CardRepository cards, SharedDeckContent content, DeckGroupRepository groups) {
        this.decks = decks; this.cards = cards; this.content = content; this.groups = groups;
    }

    @Transactional(readOnly = true)
    public Link status(UUID userId, UUID deckId) { return link(owned(userId, deckId)); }

    Link link(Deck deck) {
        return new Link(deck.getShareToken(), deck.isSharePersonalNotes(), ShareCodes.display(deck.getShareCode()), deck.getShareExpiresAt(),
                new Library(deck.getVisibility() == DomainTypes.Visibility.PUBLIC, deck.getLibraryLevel(), deck.getModerationStatus(), deck.getRemovedReason(), deck.getDownloadCount()));
    }

    /** Creates the link and its short code if missing. Decks shared before codes existed get one here. */
    void ensureShare(Deck deck) {
        if (deck.getShareToken() == null) deck.setShareToken(UUID.randomUUID().toString());
        if (deck.getShareCode() == null) deck.setShareCode(ShareCodes.generate(code -> decks.existsByShareCode(code) || groups.existsByShareCode(code)));
    }

    void requireNotRemoved(Deck deck) {
        if ("REMOVED".equals(deck.getModerationStatus()))
            throw ApiException.conflict("Bộ thẻ này đã bị gỡ khỏi chia sẻ" + (deck.getRemovedReason() == null ? "" : ": " + deck.getRemovedReason()));
    }

    @Transactional
    public Link enable(UUID userId, UUID deckId) {
        return enable(userId, deckId, null);
    }

    @Transactional
    public Link enable(UUID userId, UUID deckId, ShareOptions options) {
        content.lockOwners(userId);
        Deck deck = owned(userId, deckId);
        em.refresh(deck, LockModeType.PESSIMISTIC_WRITE);
        requireActiveImport(deck);
        requireNotRemoved(deck);
        ensureShare(deck);
        boolean published = deck.getVisibility() == DomainTypes.Visibility.PUBLIC;
        if (options != null) {
            deck.setSharePersonalNotes(options.includePersonalNotes());
            if (published) deck.setShareExpiresAt(null);
            else if (!options.keepsExpiry()) deck.setShareExpiresAt(ShareCodes.expiry(options.expiresInDays()));
        } else if (ShareCodes.expired(deck.getShareExpiresAt())) deck.setShareExpiresAt(null);
        return link(deck);
    }

    @Transactional
    public void revoke(UUID userId, UUID deckId) {
        content.lockOwners(userId);
        Deck deck = owned(userId, deckId);
        em.refresh(deck, LockModeType.PESSIMISTIC_WRITE);
        requireActiveImport(deck);
        deck.setShareToken(null);
        deck.setShareCode(null);
        deck.setShareExpiresAt(null);
        deck.setSharePersonalNotes(false);
        // A deck with no link cannot stay in the library.
        deck.setVisibility(DomainTypes.Visibility.PRIVATE);
        deck.setPublishedAt(null);
    }

    @Transactional(readOnly = true)
    public Preview preview(String token, int page) {
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Trang xem trước không hợp lệ");
        return preview(shared(token), page);
    }

    Preview preview(Deck deck, int page) {
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Trang xem trước không hợp lệ");
        int count = Math.toIntExact(cards.countByDeckIdAndDeletedAtIsNull(deck.getId()));
        int previewCount = (count + 1) / 2;
        int size = 50;
        long offset = (long) page * size;
        int remaining = (int) Math.max(0, previewCount - offset);
        List<PreviewCard> previewCards = remaining == 0 ? List.of() :
                cards.findAllByDeckIdAndDeletedAtIsNull(deck.getId(), org.springframework.data.domain.PageRequest.of(
                        page, size, org.springframework.data.domain.Sort.by("position", "id"))).stream()
                        .limit(Math.min(size, remaining))
                        .map(c -> new PreviewCard(c.getFront(), c.getBack(), c.getReading(), c.getKind(), c.getDictionaryProvenance() != null)).toList();
        return new Preview(deck.getName(), deck.getDescription(), deck.getOwner().getDisplayName(), count,
                previewCount, page, size, (previewCount + size - 1) / size, previewCards);
    }

    @Transactional
    public CopyResult copy(UUID userId, String token) {
        Deck source = shared(token);
        // All share and library mutations take user locks before deck locks, in the same order.
        content.lockOwners(source.getOwner().getId(), userId);
        em.refresh(source, LockModeType.PESSIMISTIC_READ);
        if (!token.equals(source.getShareToken()) || source.getDeletedAt() != null || ShareCodes.expired(source.getShareExpiresAt())) throw unavailable();
        if (source.getOwner().getId().equals(userId)) return new CopyResult(source.getId(), true);
        String key = "share:" + source.getId();
        // A locking read sees the latest committed copy even under MySQL REPEATABLE READ.
        // The initial shared-link lookup may already have established an older snapshot.
        Optional<Deck> previous = decks.findSharedCopyForUpdate(userId, key);
        if (previous.isPresent() && previous.get().getDeletedAt() == null)
            return new CopyResult(previous.get().getId(), true);
        // Allow saving again after the recipient intentionally deleted their earlier copy.
        previous.ifPresent(deck -> deck.setImportKey(null));
        em.flush();
        content.requireCardLimit(List.of(source.getId()));
        UUID copied = content.copy(userId, source, key);
        // Atomic increment: counting a download must not conflict with the owner editing the deck.
        if (source.getVisibility() == DomainTypes.Visibility.PUBLIC) decks.incrementDownloads(source.getId());
        return new CopyResult(copied, false);
    }

    private Deck owned(UUID userId, UUID deckId) {
        Deck deck = decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        requireActiveImport(deck);
        return deck;
    }

    private void requireActiveImport(Deck deck) {
        if (deck.getDeletedAt() != null) throw unavailable();
        if (deck.getSourceType() != DomainTypes.SourceType.IMPORT)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ chia sẻ bộ thẻ tự import");
    }

    private Deck shared(String token) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) throw unavailable();
        Deck deck = decks.findByShareTokenAndDeletedAtIsNull(token).orElseThrow(this::unavailable);
        if (ShareCodes.expired(deck.getShareExpiresAt())) throw unavailable();
        requireActiveImport(deck);
        return deck;
    }

    private ApiException unavailable() { return ApiException.notFound("Liên kết không tồn tại hoặc đã ngừng chia sẻ."); }
}

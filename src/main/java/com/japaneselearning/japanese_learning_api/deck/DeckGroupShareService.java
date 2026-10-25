package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DeckGroupShareService {
    public record Link(String token, int deckCount, int excludedDeckCount, long cardCount, boolean includePersonalNotes, String code, java.time.Instant expiresAt) {
        public Link(String token, int deckCount, int excludedDeckCount, long cardCount, boolean includePersonalNotes) { this(token, deckCount, excludedDeckCount, cardCount, includePersonalNotes, null, null); }
    }
    public record PreviewDeck(UUID id, String name, String description, long cardCount) {}
    public record Preview(String name, String author, int deckCount, int excludedDeckCount, long cardCount,
                          int page, int size, int totalPages, List<PreviewDeck> decks) {}
    public record CopyResult(UUID groupId, List<UUID> deckIds, boolean alreadySaved) {}
    private final DeckGroupRepository groups;
    private final DeckRepository decks;
    private final CardRepository cards;
    private final DeckShareService deckSharing;
    private final SharedDeckContent content;
    @PersistenceContext private EntityManager em;

    public DeckGroupShareService(DeckGroupRepository groups, DeckRepository decks, CardRepository cards,
                                 DeckShareService deckSharing, SharedDeckContent content) {
        this.groups = groups; this.decks = decks; this.cards = cards;
        this.deckSharing = deckSharing; this.content = content;
    }
    @Transactional(readOnly = true)
    public Link status(UUID userId, UUID groupId) {
        return link(owned(userId, groupId));
    }
    @Transactional
    public Link enable(UUID userId, UUID groupId) {
        return enable(userId, groupId, null);
    }
    @Transactional
    public Link enable(UUID userId, UUID groupId, DeckShareService.ShareOptions options) {
        content.lockOwners(userId);
        DeckGroup group = owned(userId, groupId);
        em.refresh(group, LockModeType.PESSIMISTIC_WRITE);
        if (eligible(members(group)).isEmpty()) throw empty();
        if (group.getShareToken() == null) group.setShareToken(UUID.randomUUID().toString());
        if (group.getShareCode() == null) group.setShareCode(ShareCodes.generate(code -> decks.existsByShareCode(code) || groups.existsByShareCode(code)));
        if (options != null) {
            group.setSharePersonalNotes(options.includePersonalNotes());
            if (!options.keepsExpiry()) group.setShareExpiresAt(ShareCodes.expiry(options.expiresInDays()));
        } else if (ShareCodes.expired(group.getShareExpiresAt())) group.setShareExpiresAt(null);
        return link(group);
    }
    @Transactional
    public void revoke(UUID userId, UUID groupId) {
        content.lockOwners(userId);
        DeckGroup group = owned(userId, groupId);
        em.refresh(group, LockModeType.PESSIMISTIC_WRITE);
        group.setShareToken(null);
        group.setShareCode(null);
        group.setShareExpiresAt(null);
        group.setSharePersonalNotes(false);
    }
    @Transactional(readOnly = true)
    public Preview preview(String token, int page) {
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Trang xem trước không hợp lệ");
        DeckGroup group = shared(token);
        var all = members(group);
        var included = eligible(all);
        var counts = counts(included);
        int size = 20;
        long offset = (long) page * size;
        var rows = included.stream().skip(offset).limit(size)
                .map(d -> new PreviewDeck(d.getId(), d.getName(), d.getDescription(), counts.getOrDefault(d.getId(), 0L))).toList();
        return new Preview(group.getName(), group.getOwner().getDisplayName(), included.size(), all.size() - included.size(),
                counts.values().stream().mapToLong(Long::longValue).sum(), page, size, (included.size() + size - 1) / size, rows);
    }
    @Transactional(readOnly = true)
    public DeckShareService.Preview previewDeck(String token, UUID deckId, int page) {
        DeckGroup group = shared(token);
        Deck deck = decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, group.getOwner().getId()).orElseThrow(this::unavailable);
        if (!group.getId().equals(deck.getGroupId()) || deck.getSourceType() != DomainTypes.SourceType.IMPORT) throw unavailable();
        return deckSharing.preview(deck, page);
    }
    @Transactional
    public CopyResult copy(UUID userId, String token) {
        DeckGroup source = shared(token);
        UUID sourceOwner = source.getOwner().getId();
        content.lockOwners(sourceOwner, userId);
        // Owner locks coordinate membership/content changes and dissolution; refresh invalidates the initial snapshot.
        try { em.refresh(source, LockModeType.PESSIMISTIC_READ); }
        catch (EntityNotFoundException ex) { throw unavailable(); }
        if (!token.equals(source.getShareToken()) || ShareCodes.expired(source.getShareExpiresAt())) throw unavailable();
        var sourceDecks = eligible(decks.findGroupForShareCopy(sourceOwner, source.getId()));
        if (sourceOwner.equals(userId)) return new CopyResult(source.getId(), sourceDecks.stream().map(Deck::getId).toList(), true);
        String key = "share-group:" + source.getId();
        var previous = groups.findSharedCopyForUpdate(userId, key);
        if (previous.isPresent()) {
            var group = previous.get();
            return new CopyResult(group.getId(), decks.findGroupForShareCopy(userId, group.getId()).stream().map(Deck::getId).toList(), true);
        }
        if (sourceDecks.isEmpty()) throw empty();
        if (sourceDecks.size() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể nhận tối đa 100 bộ import trong một nhóm");
        content.requireCardLimit(sourceDecks.stream().map(Deck::getId).toList());
        var target = new DeckGroup();
        target.setId(UUID.randomUUID());
        target.setOwner(em.getReference(User.class, userId));
        target.setName(source.getName());
        target.setPosition(groups.findAllByOwnerIdOrderByPositionAscIdAsc(userId).size());
        target.setImportKey(key);
        em.persist(target);
        var deckIds = new ArrayList<UUID>();
        for (int i = 0; i < sourceDecks.size(); i++) {
            Deck original = sourceDecks.get(i);
            // Scope to the received group instance. After dissolution, old standalone copies are never moved or overwritten.
            UUID id = content.copy(userId, original, "share-group:" + target.getId() + ":" + original.getId(), source.isSharePersonalNotes());
            Deck copy = em.find(Deck.class, id);
            copy.setGroupId(target.getId());
            copy.setPosition(i);
            deckIds.add(id);
        }
        return new CopyResult(target.getId(), deckIds, false);
    }
    private Link link(DeckGroup group) {
        var all = members(group);
        var included = eligible(all);
        return new Link(group.getShareToken(), included.size(), all.size() - included.size(),
                counts(included).values().stream().mapToLong(Long::longValue).sum(), group.isSharePersonalNotes(),
                ShareCodes.display(group.getShareCode()), group.getShareExpiresAt());
    }
    private Map<UUID, Long> counts(List<Deck> included) {
        var counts = new HashMap<UUID, Long>();
        if (!included.isEmpty()) cards.countSharedCards(included.stream().map(Deck::getId).toList())
                .forEach(c -> counts.put(c.getDeckId(), c.getCardCount()));
        return counts;
    }
    private List<Deck> members(DeckGroup group) {
        return decks.findAllByOwnerIdAndGroupIdAndDeletedAtIsNullOrderByPositionAscIdAsc(group.getOwner().getId(), group.getId());
    }
    private List<Deck> eligible(List<Deck> all) {
        return all.stream().filter(d -> d.getSourceType() == DomainTypes.SourceType.IMPORT).toList();
    }
    private DeckGroup owned(UUID userId, UUID groupId) {
        return groups.findByIdAndOwnerId(groupId, userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhóm"));
    }
    private DeckGroup shared(String token) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) throw unavailable();
        DeckGroup group = groups.findByShareToken(token).orElseThrow(this::unavailable);
        if (ShareCodes.expired(group.getShareExpiresAt())) throw unavailable();
        return group;
    }
    private ApiException unavailable() { return ApiException.notFound("Liên kết không tồn tại hoặc đã ngừng chia sẻ."); }
    private ApiException empty() { return new ApiException(HttpStatus.BAD_REQUEST, "Nhóm chưa có bộ import để chia sẻ"); }
}

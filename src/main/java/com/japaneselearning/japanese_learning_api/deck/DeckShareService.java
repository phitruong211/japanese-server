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

import java.util.*;

@Service
public class DeckShareService {
    public record Link(String token) {}
    public record PreviewCard(String front, String back, String reading, DomainTypes.CardKind kind, boolean dictionarySource) {}
    public record Preview(String name, String description, String author, int cardCount,
                          int previewCount, int page, int size, int totalPages, List<PreviewCard> cards) {}
    public record CopyResult(UUID deckId, boolean alreadySaved) {}

    private final DeckRepository decks;
    private final CardRepository cards;
    private final DeckService deckService;
    @PersistenceContext private EntityManager em;

    public DeckShareService(DeckRepository decks, CardRepository cards, DeckService deckService) {
        this.decks = decks; this.cards = cards; this.deckService = deckService;
    }

    @Transactional(readOnly = true)
    public Link status(UUID userId, UUID deckId) { return new Link(owned(userId, deckId).getShareToken()); }

    @Transactional
    public Link enable(UUID userId, UUID deckId) {
        Deck deck = owned(userId, deckId);
        em.refresh(deck, LockModeType.PESSIMISTIC_WRITE);
        requireActiveImport(deck);
        if (deck.getShareToken() == null) deck.setShareToken(UUID.randomUUID().toString());
        return new Link(deck.getShareToken());
    }

    @Transactional
    public void revoke(UUID userId, UUID deckId) {
        Deck deck = owned(userId, deckId);
        em.refresh(deck, LockModeType.PESSIMISTIC_WRITE);
        requireActiveImport(deck);
        deck.setShareToken(null);
    }

    @Transactional(readOnly = true)
    public Preview preview(String token, int page) {
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Trang xem trước không hợp lệ");
        Deck deck = shared(token);
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
        // Serialize copying with revocation, then serialize retries for the recipient.
        em.refresh(source, LockModeType.PESSIMISTIC_READ);
        if (!token.equals(source.getShareToken()) || source.getDeletedAt() != null) throw unavailable();
        if (source.getOwner().getId().equals(userId)) return new CopyResult(source.getId(), true);
        if (em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null)
            throw ApiException.notFound("Không tìm thấy người dùng");
        String key = "share:" + source.getId();
        // A locking read sees the latest committed copy even under MySQL REPEATABLE READ.
        // The initial shared-link lookup may already have established an older snapshot.
        Optional<Deck> previous = decks.findSharedCopyForUpdate(userId, key);
        if (previous.isPresent() && previous.get().getDeletedAt() == null)
            return new CopyResult(previous.get().getId(), true);
        // Allow saving again after the recipient intentionally deleted their earlier copy.
        previous.ifPresent(deck -> deck.setImportKey(null));
        em.flush();
        var sourceCards = cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(source.getId());
        if (sourceCards.size() > 20_000) throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ thẻ vượt giới hạn 20.000 thẻ");
        var requests = sourceCards.stream().map(c -> new DeckDtos.CardRequest(
                c.getFront(), c.getBack(), c.getReading(), c.getNotes(), c.getKind(), c.getPosition(), null,
                sharedExtraData(c), null)).toList();
        var result = deckService.create(userId, new DeckDtos.CreateDeckRequest(
                source.getName(), source.getDescription(), DomainTypes.SourceType.IMPORT,
                "Bộ thẻ được chia sẻ", "SHARED", DomainTypes.Visibility.PRIVATE,
                new HashMap<>(source.getTemplateConfig()), requests, "IMPORT", null, null, source.getTags()));
        em.find(Deck.class, result.deck().id()).setImportKey(key);
        for (int i = 0; i < sourceCards.size(); i++) {
            Card original = sourceCards.get(i);
            Card copied = em.find(Card.class, result.cards().get(i).id());
            copied.setDictionaryIdentity(original.getDictionaryIdentity());
            copied.setDictionaryProvenance(original.getDictionaryProvenance());
            if (original.getDictionaryProvenance() != null) copied.getExtraData().put("dictionaryRef", original.getDictionaryProvenance());
        }
        return new CopyResult(result.deck().id(), false);
    }

    private Deck owned(UUID userId, UUID deckId) {
        Deck deck = decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        requireActiveImport(deck);
        return deck;
    }

    private Map<String, Object> sharedExtraData(Card card) {
        Map<String, Object> data = new HashMap<>(card.getExtraData());
        if (card.getDictionaryProvenance() != null) data.put("dictionaryRef", card.getDictionaryProvenance());
        data.remove("sourceRef");
        data.remove("sourceSheet");
        return data;
    }

    private void requireActiveImport(Deck deck) {
        if (deck.getDeletedAt() != null) throw unavailable();
        if (deck.getSourceType() != DomainTypes.SourceType.IMPORT)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ chia sẻ bộ thẻ tự import");
    }

    private Deck shared(String token) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) throw unavailable();
        Deck deck = decks.findByShareTokenAndDeletedAtIsNull(token).orElseThrow(this::unavailable);
        requireActiveImport(deck);
        return deck;
    }

    private ApiException unavailable() { return ApiException.notFound("Liên kết không tồn tại hoặc đã ngừng chia sẻ."); }
}

package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.Card;
import com.japaneselearning.japanese_learning_api.model.Deck;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import com.japaneselearning.japanese_learning_api.model.User;
import com.japaneselearning.japanese_learning_api.repository.CardRepository;
import com.japaneselearning.japanese_learning_api.repository.DeckRepository;
import com.japaneselearning.japanese_learning_api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DeckService {
    private final DeckRepository decks;
    private final CardRepository cards;
    private final UserRepository users;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final tools.jackson.databind.ObjectMapper mapper;

    public DeckService(DeckRepository decks, CardRepository cards, UserRepository users, tools.jackson.databind.ObjectMapper mapper) {
        this.mapper = mapper;
        this.decks = decks;
        this.cards = cards;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<DeckDtos.DeckSummary> list(UUID userId) {
        return decks.findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(userId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public DeckDtos.DeckResponse get(UUID userId, UUID deckId) {
        Deck deck = ownedDeck(userId, deckId);
        return new DeckDtos.DeckResponse(summary(deck), cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(deckId)
                .stream().map(this::cardResponse).toList());
    }

    @Transactional
    public DeckDtos.DeckResponse create(UUID userId, DeckDtos.CreateDeckRequest request) {
        User owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        Deck deck = new Deck();
        deck.setId(UUID.randomUUID());
        deck.setOwner(owner);
        validateRequest(request);
        deck.setName(required(request.name()));
        deck.setSource(request.source() == null ? (request.sourceType() == DomainTypes.SourceType.IMPORT ? "IMPORT" : request.sourceType() == DomainTypes.SourceType.SYSTEM ? "BUILT_IN" : "MANUAL") : request.source());
        deck.setSourceRef(blankToNull(request.sourceRef()));
        deck.setSourceSheet(blankToNull(request.sourceSheet()));
        deck.setTags(request.tags() == null ? List.of() : List.copyOf(request.tags()));
        deck.setDescription(blankToNull(request.description()));
        deck.setSourceType(request.sourceType() == null ? DomainTypes.SourceType.MANUAL : request.sourceType());
        deck.setSourceName(blankToNull(request.sourceName()));
        deck.setImportFormat(request.importFormat() == null ? null : request.importFormat().trim().toUpperCase(Locale.ROOT));
        deck.setVisibility(request.visibility() == null ? DomainTypes.Visibility.PRIVATE : request.visibility());
        if (request.templateConfig() != null) deck.setTemplateConfig(validateTemplateConfig(request.templateConfig()));
        deck.setPosition((int) decks.countByOwnerIdAndDeletedAtIsNull(userId));
        List<DeckDtos.CardRequest> requestedCards = request.cards() == null ? List.of() : request.cards();
        deck.setCardCount(requestedCards.size());
        decks.save(deck);
        List<Card> savedCards = new ArrayList<>();
        for (int i = 0; i < requestedCards.size(); i++) {
            savedCards.add(cards.save(newCard(deck, requestedCards.get(i), i)));
        }
        return new DeckDtos.DeckResponse(summary(deck), savedCards.stream().map(this::cardResponse).toList());
    }

    @Transactional
    public DeckDtos.DeckSummary update(UUID userId, UUID deckId, DeckDtos.UpdateDeckRequest request) {
        Deck deck = ownedDeck(userId, deckId);
        if (request.name() != null) deck.setName(required(request.name()));
        if (request.description() != null) deck.setDescription(blankToNull(request.description()));
        if (request.visibility() != null) deck.setVisibility(request.visibility());
        if (request.templateConfig() != null) deck.setTemplateConfig(validateTemplateConfig(request.templateConfig()));
        return summary(deck);
    }

    @Transactional
    public List<DeckDtos.DeckSummary> reorderDecks(UUID userId, DeckDtos.ReorderDecksRequest request) {
        List<Deck> owned = decks.findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(userId);
        requireExactIds(request.deckIds(), owned.stream().map(Deck::getId).toList(), "Danh sách bộ thẻ không hợp lệ");
        Map<UUID, Deck> byId = new HashMap<>();
        owned.forEach(deck -> byId.put(deck.getId(), deck));
        for (int i = 0; i < request.deckIds().size(); i++) byId.get(request.deckIds().get(i)).setPosition(i);
        return request.deckIds().stream().map(id -> summary(byId.get(id))).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID deckId) { ownedDeck(userId, deckId).setDeletedAt(Instant.now()); }

    @Transactional
    public DeckDtos.CardResponse addCard(UUID userId, UUID deckId, DeckDtos.CardRequest request) {
        Deck deck = ownedDeck(userId, deckId);
        int position = request.position() == null ? deck.getCardCount() : request.position();
        Card card = cards.save(newCard(deck, request, position));
        deck.setCardCount((int) cards.countByDeckIdAndDeletedAtIsNull(deckId));
        return cardResponse(card);
    }

    @Transactional
    public DeckDtos.CardResponse updateCard(UUID userId, UUID cardId, DeckDtos.UpdateCardRequest request) {
        Card card = ownedCard(userId, cardId);
        if (request.front() != null) card.setFront(required(request.front()));
        if (request.back() != null) card.setBack(required(request.back()));
        if (request.reading() != null) card.setReading(blankToNull(request.reading()));
        if (request.notes() != null) card.setNotes(blankToNull(request.notes()));
        if (request.kind() != null) card.setKind(request.kind());
        if (request.position() != null) card.setPosition(request.position());
        if (request.extraData() != null) card.setExtraData(new HashMap<>(request.extraData()));
        if (request.tags() != null) card.getExtraData().put("tags", List.copyOf(request.tags()));
        return cardResponse(card);
    }

    @Transactional
    public List<DeckDtos.CardResponse> reorderCards(UUID userId, UUID deckId, DeckDtos.ReorderCardsRequest request) {
        ownedDeck(userId, deckId);
        List<Card> owned = cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(deckId);
        requireExactIds(request.cardIds(), owned.stream().map(Card::getId).toList(), "Danh sách thẻ không hợp lệ");
        Map<UUID, Card> byId = new HashMap<>();
        owned.forEach(card -> byId.put(card.getId(), card));
        for (int i = 0; i < request.cardIds().size(); i++) byId.get(request.cardIds().get(i)).setPosition(i);
        return request.cardIds().stream().map(id -> cardResponse(byId.get(id))).toList();
    }

    @Transactional
    public List<DeckDtos.CardResponse> moveCards(UUID userId, DeckDtos.MoveCardsRequest request) {
        Deck target = ownedDeck(userId, request.targetDeckId());
        if (new HashSet<>(request.cardIds()).size() != request.cardIds().size())
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Danh sách thẻ bị trùng");
        List<Card> moving = request.cardIds().stream().map(id -> ownedCard(userId, id)).toList();
        Set<Deck> sources = new HashSet<>();
        moving.forEach(card -> sources.add(card.getDeck()));
        List<Card> targetCards = new ArrayList<>(cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(target.getId()));
        Set<UUID> movingIds = new HashSet<>(request.cardIds());
        targetCards.removeIf(card -> movingIds.contains(card.getId()));
        int insertAt = Math.min(request.targetPosition() == null ? targetCards.size() : request.targetPosition(), targetCards.size());
        moving.forEach(card -> card.setDeck(target));
        targetCards.addAll(insertAt, moving);
        normalizePositions(targetCards);
        for (Deck source : sources) {
            if (!source.getId().equals(target.getId())) {
                List<Card> remaining = cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(source.getId()).stream()
                        .filter(card -> !movingIds.contains(card.getId())).toList();
                normalizePositions(remaining);
                source.setCardCount(remaining.size());
            }
        }
        target.setCardCount(targetCards.size());
        return moving.stream().map(this::cardResponse).toList();
    }

    @Transactional
    public void deleteCard(UUID userId, UUID cardId) {
        Card card = ownedCard(userId, cardId);
        card.setDeletedAt(Instant.now());
        Deck deck = card.getDeck();
        deck.setCardCount(Math.max(0, deck.getCardCount() - 1));
    }

    private Deck ownedDeck(UUID userId, UUID deckId) {
        return decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
    }

    private Card ownedCard(UUID userId, UUID cardId) {
        return cards.findByIdAndDeckOwnerIdAndDeletedAtIsNull(cardId, userId).filter(c -> c.getDeck().getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thẻ"));
    }

    private Card newCard(Deck deck, DeckDtos.CardRequest request, int defaultPosition) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setDeck(deck);
        card.setSource(deck.getSource());
        card.setSourceRef(deck.getSourceRef());
        card.setSourceSheet(deck.getSourceSheet());
        card.setFront(required(request.front()));
        card.setBack(required(request.back()));
        card.setReading(blankToNull(request.reading()));
        card.setNotes(blankToNull(request.notes()));
        card.setKind(request.kind() == null ? DomainTypes.CardKind.GENERAL : request.kind());
        card.setPosition(request.position() == null ? defaultPosition : request.position());
        card.setExternalId(blankToNull(request.externalId()));
        card.setExtraData(request.extraData() == null ? new HashMap<>() : new HashMap<>(request.extraData()));
        if (request.tags() != null) card.getExtraData().put("tags", List.copyOf(request.tags()));
        if (card.getExtraData().get("sourceRef") instanceof String ref) {
            if (ref.length() > 1000) throw bad("Tên nguồn quá dài");
            card.setSourceRef(blankToNull(ref));
        }
        if (card.getExtraData().get("sourceSheet") instanceof String sheet) {
            if (sheet.length() > 255) throw bad("Tên sheet quá dài");
            card.setSourceSheet(blankToNull(sheet));
        }
        return card;
    }

    private DeckDtos.DeckSummary summary(Deck deck) {
        return new DeckDtos.DeckSummary(deck.getId(), deck.getName(), deck.getDescription(), deck.getSourceType(),
                deck.getSourceName(), deck.getImportFormat(), deck.getVisibility(), deck.getCardCount(),
                deck.getPosition(), deck.getTemplateConfig(), deck.getCreatedAt(), deck.getUpdatedAt(),
                deck.getSource(), deck.getSourceRef(), deck.getSourceSheet(), deck.getTags() == null ? List.of() : deck.getTags(),
                cards.countNew(deck.getId(), deck.getOwner().getId()), cards.countDue(deck.getId(), deck.getOwner().getId(), Instant.now()));
    }

    private DeckDtos.CardResponse cardResponse(Card card) {
        return new DeckDtos.CardResponse(card.getId(), card.getDeck().getId(), card.getFront(), card.getBack(),
                card.getReading(), card.getNotes(), card.getKind(), card.getPosition(), card.getExternalId(),
                card.getExtraData(), card.getCreatedAt(), card.getUpdatedAt(), cardTags(card), null,
                card.getSource(), card.getSourceRef(), card.getSourceSheet());
    }

    @Transactional(readOnly = true)
    public DeckDtos.PageResponse<DeckDtos.DeckSummary> listPage(UUID userId, int page, int size, String sort) {
        String[] parts = sort.split(",");
        if (!Set.of("updatedAt", "createdAt", "name", "position").contains(parts[0])) throw bad("Trường sắp xếp không hợp lệ");
        var direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        return DeckDtos.PageResponse.from(decks.findAllByOwnerIdAndDeletedAtIsNull(userId, pageRequest(page, size, org.springframework.data.domain.Sort.by(direction, parts[0]).and(org.springframework.data.domain.Sort.by("id")))).map(this::summary));
    }

    @Transactional(readOnly = true)
    public DeckDtos.DeckSummary metadata(UUID userId, UUID deckId) { return summary(ownedDeck(userId, deckId)); }

    @Transactional(readOnly = true)
    public DeckDtos.PageResponse<DeckDtos.CardResponse> cardPage(UUID userId, UUID deckId, int page, int size, String query, String type, String state) {
        ownedDeck(userId, deckId);
        final DomainTypes.CardKind kind;
        final DomainTypes.AnkiState progressState;
        try {
            kind = type == null || type.isBlank() ? null : DomainTypes.CardKind.valueOf(type.toUpperCase(Locale.ROOT));
            progressState = state == null || state.isBlank() || state.equalsIgnoreCase("DUE") ? null : DomainTypes.AnkiState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) { throw bad("Bộ lọc không hợp lệ"); }
        var result = cards.findAll((org.springframework.data.jpa.domain.Specification<Card>) (root, cq, cb) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            filters.add(cb.equal(root.get("deck").get("id"), deckId));
            filters.add(cb.isNull(root.get("deletedAt")));
            if (kind != null) filters.add(cb.equal(root.get("kind"), kind));
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                filters.add(cb.or(cb.like(cb.lower(root.get("front")), pattern, '!'), cb.like(cb.lower(root.get("back")), pattern, '!'), cb.like(cb.lower(root.get("reading")), pattern, '!'), cb.like(cb.lower(root.get("notes")), pattern, '!')));
            }
            if (state != null && !state.isBlank()) {
                var sub = cq.subquery(UUID.class);
                var progress = sub.from(com.japaneselearning.japanese_learning_api.model.AnkiCardProgress.class);
                var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
                conditions.add(cb.equal(progress.get("card").get("id"), root.get("id")));
                conditions.add(cb.equal(progress.get("user").get("id"), userId));
                if (progressState == DomainTypes.AnkiState.NEW) conditions.add(cb.notEqual(progress.get("state"), DomainTypes.AnkiState.NEW));
                else if (progressState != null) conditions.add(cb.equal(progress.get("state"), progressState));
                else { conditions.add(cb.lessThanOrEqualTo(progress.get("dueAt"), Instant.now())); conditions.add(cb.notEqual(progress.get("state"), DomainTypes.AnkiState.NEW)); }
                sub.select(progress.get("id")).where(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
                filters.add(progressState == DomainTypes.AnkiState.NEW ? cb.not(cb.exists(sub)) : cb.exists(sub));
            }
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageRequest(page, size, org.springframework.data.domain.Sort.by("position", "id")));
        var ids = result.getContent().stream().map(Card::getId).toList();
        Map<UUID, com.japaneselearning.japanese_learning_api.model.AnkiCardProgress> byId = new HashMap<>();
        if (!ids.isEmpty()) em.createQuery("select p from AnkiCardProgress p where p.user.id = :user and p.card.id in :ids", com.japaneselearning.japanese_learning_api.model.AnkiCardProgress.class)
                .setParameter("user", userId).setParameter("ids", ids).getResultList().forEach(p -> byId.put(p.getCard().getId(), p));
        return DeckDtos.PageResponse.from(result.map(card -> {
            var r = cardResponse(card); var p = byId.get(card.getId());
            var progress = p == null ? null : new DeckDtos.ProgressResponse(p.getState(), p.getDueAt(), p.getLastReviewedAt(), p.getRepetitions(), p.getLapses(), p.getEaseFactor(), p.getIntervalMinutes(), p.getIntervalDays());
            return new DeckDtos.CardResponse(r.id(), r.deckId(), r.front(), r.back(), r.reading(), r.notes(), r.kind(), r.position(), r.externalId(), r.extraData(), r.createdAt(), r.updatedAt(), r.tags(), progress, r.source(), r.sourceRef(), r.sourceSheet());
        }));
    }

    @Transactional
    public DeckDtos.DeckResponse importDeck(UUID userId, String key, DeckDtos.CreateDeckRequest request) {
        if (key == null || key.isBlank() || key.length() > 128) throw bad("Idempotency-Key bắt buộc và tối đa 128 ký tự");
        validateRequest(request);
        // Serialize imports for one owner so concurrent retries cannot both create a deck.
        if (em.find(User.class, userId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) == null) throw ApiException.notFound("Không tìm thấy người dùng");
        String hash;
        try { hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var previous = decks.findByOwnerIdAndImportKey(userId, key);
        if (previous.isPresent()) {
            var deck = previous.get();
            if (!hash.equals(deck.getImportHash())) throw ApiException.conflict("Khóa import đã dùng cho nội dung khác");
            if (deck.getDeletedAt() != null) throw ApiException.conflict("Bộ thẻ import đã bị xóa");
            return get(userId, deck.getId());
        }
        var response = create(userId, request);
        var deck = ownedDeck(userId, response.deck().id());
        deck.setImportKey(key); deck.setImportHash(hash);
        decks.flush();
        return response;
    }

    private void validateRequest(DeckDtos.CreateDeckRequest request) {
        required(request.name());
        if (request.cards() != null && request.cards().size() > 20_000) throw bad("Tối đa 20.000 thẻ");
        if (mapper.writeValueAsBytes(request).length > 20 * 1024 * 1024) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Dữ liệu vượt 20 MB");
        if (request.cards() != null) for (var card : request.cards()) {
            if (card == null) throw bad("Thẻ không hợp lệ"); required(card.front()); required(card.back());
        }
    }
    @Transactional(readOnly = true)
    public List<UUID> cardIds(UUID userId, UUID deckId) {
        ownedDeck(userId, deckId);
        return em.createQuery("select c.id from Card c where c.deck.id = :deck and c.deletedAt is null order by c.position, c.id", UUID.class).setParameter("deck", deckId).getResultList();
    }
    private String required(String value) { if (value == null || value.isBlank()) throw bad("Tên, mặt trước và mặt sau không được chỉ chứa khoảng trắng"); return value.trim(); }
    private ApiException bad(String message) { return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, message); }
    private org.springframework.data.domain.PageRequest pageRequest(int page, int size, org.springframework.data.domain.Sort sort) {
        if (page < 0 || size < 1 || size > 200) throw bad("Trang phải >= 0 và kích thước từ 1 đến 200");
        return org.springframework.data.domain.PageRequest.of(page, size, sort);
    }
    private List<String> cardTags(Card card) {
        Object value = card.getExtraData().get("tags");
        return value instanceof List<?> list ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void normalizePositions(List<Card> ordered) {
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }

    private void requireExactIds(List<UUID> requested, List<UUID> expected, String message) {
        if (requested.size() != expected.size() || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(requested).equals(new HashSet<>(expected)))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateTemplateConfig(Map<String, Object> config) {
        if (config.size() > 10) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Cấu hình thẻ quá lớn");
        Set<String> allowedRoot = Set.of("version", "front", "back", "style", "study");
        if (!allowedRoot.containsAll(config.keySet()))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Cấu hình thẻ chứa thuộc tính không hỗ trợ");
        Object styleValue = config.get("style");
        if (styleValue instanceof Map<?, ?> style) {
            requireAllowed(style, "theme", Set.of("paper", "blue", "dark", "system"));
            requireAllowed(style, "fontScale", Set.of("small", "medium", "large", "xlarge"));
            requireAllowed(style, "alignment", Set.of("left", "center"));
        }
        Object studyValue = config.get("study");
        if (studyValue instanceof Map<?, ?> study)
            requireAllowed(study, "orientation", Set.of("front-first", "back-first", "mixed"));
        return new HashMap<>(config);
    }

    private void requireAllowed(Map<?, ?> values, String key, Set<String> allowed) {
        Object value = values.get(key);
        if (value != null && !allowed.contains(String.valueOf(value)))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Giá trị " + key + " không được hỗ trợ");
    }
}

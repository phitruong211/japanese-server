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

    public DeckService(DeckRepository decks, CardRepository cards, UserRepository users) {
        this.decks = decks;
        this.cards = cards;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<DeckDtos.DeckSummary> list(UUID userId) {
        return decks.findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream().map(this::summary).toList();
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
        deck.setName(request.name().trim());
        deck.setDescription(blankToNull(request.description()));
        deck.setSourceType(request.sourceType() == null ? DomainTypes.SourceType.MANUAL : request.sourceType());
        deck.setSourceName(blankToNull(request.sourceName()));
        deck.setImportFormat(request.importFormat() == null ? null : request.importFormat().trim().toUpperCase(Locale.ROOT));
        deck.setVisibility(request.visibility() == null ? DomainTypes.Visibility.PRIVATE : request.visibility());
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
        if (request.name() != null) deck.setName(request.name().trim());
        if (request.description() != null) deck.setDescription(blankToNull(request.description()));
        if (request.visibility() != null) deck.setVisibility(request.visibility());
        return summary(deck);
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
        if (request.front() != null) card.setFront(request.front().trim());
        if (request.back() != null) card.setBack(request.back().trim());
        if (request.reading() != null) card.setReading(blankToNull(request.reading()));
        if (request.notes() != null) card.setNotes(blankToNull(request.notes()));
        if (request.kind() != null) card.setKind(request.kind());
        if (request.position() != null) card.setPosition(request.position());
        if (request.extraData() != null) card.setExtraData(new HashMap<>(request.extraData()));
        return cardResponse(card);
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
        return cards.findByIdAndDeckOwnerIdAndDeletedAtIsNull(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thẻ"));
    }

    private Card newCard(Deck deck, DeckDtos.CardRequest request, int defaultPosition) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setDeck(deck);
        card.setFront(request.front().trim());
        card.setBack(request.back().trim());
        card.setReading(blankToNull(request.reading()));
        card.setNotes(blankToNull(request.notes()));
        card.setKind(request.kind() == null ? DomainTypes.CardKind.GENERAL : request.kind());
        card.setPosition(request.position() == null ? defaultPosition : request.position());
        card.setExternalId(blankToNull(request.externalId()));
        card.setExtraData(request.extraData() == null ? new HashMap<>() : new HashMap<>(request.extraData()));
        return card;
    }

    private DeckDtos.DeckSummary summary(Deck deck) {
        return new DeckDtos.DeckSummary(deck.getId(), deck.getName(), deck.getDescription(), deck.getSourceType(),
                deck.getSourceName(), deck.getImportFormat(), deck.getVisibility(), deck.getCardCount(),
                deck.getCreatedAt(), deck.getUpdatedAt());
    }

    private DeckDtos.CardResponse cardResponse(Card card) {
        return new DeckDtos.CardResponse(card.getId(), card.getDeck().getId(), card.getFront(), card.getBack(),
                card.getReading(), card.getNotes(), card.getKind(), card.getPosition(), card.getExternalId(),
                card.getExtraData(), card.getCreatedAt(), card.getUpdatedAt());
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

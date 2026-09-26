package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DeckServiceIntegrationTest {
    @Autowired DeckService service;
    @Autowired com.japaneselearning.japanese_learning_api.anki.AnkiService anki;
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;

    UUID user() {
        var user = new User(); user.setId(UUID.randomUUID()); user.setEmail(user.getId()+"@deck.test");
        user.setDisplayName("Deck test"); user.setPasswordHash("test"); return users.saveAndFlush(user).getId();
    }
    DeckDtos.CardRequest card(String front) {
        return new DeckDtos.CardRequest(front, "Meaning", "reading", "note", DomainTypes.CardKind.VOCABULARY, null, null, Map.of("custom", "preserved"), List.of("tag"));
    }
    DeckDtos.CreateDeckRequest request(String name, List<DeckDtos.CardRequest> cards) {
        return new DeckDtos.CreateDeckRequest(name, null, DomainTypes.SourceType.IMPORT, "test.csv", "CSV", null, null, cards, "IMPORT", "test.csv", "Sheet1", List.of("deck-tag"));
    }
    @Test void importIsIdempotentAndChangedPayloadConflicts() {
        UUID owner = user(); var input = request("Deck", List.of(card("one"),card("two")));
        var first = service.importDeck(owner, "same-key", input);
        var retry = service.importDeck(owner, "same-key", input);
        assertThat(retry.deck().id()).isEqualTo(first.deck().id());
        assertThat(retry.cards()).extracting(DeckDtos.CardResponse::id).containsExactlyElementsOf(first.cards().stream().map(DeckDtos.CardResponse::id).toList());
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(owner)).isEqualTo(1);
        assertThatThrownBy(() -> service.importDeck(owner, "same-key", request("changed", input.cards()))).isInstanceOf(ApiException.class);
        assertThat(service.importDeck(user(), "same-key", input).deck().id()).isNotEqualTo(first.deck().id());
    }
    @Test void paginationOwnershipFiltersAndCounts() {
        UUID owner = user(), stranger = user();
        var deck = service.create(owner, request("Deck", List.of(card("apple"), card("banana"), card("cherry"))));
        assertThat(service.listPage(stranger, 0, 20, "updatedAt,desc").totalElements()).isZero();
        assertThatThrownBy(() -> service.cardPage(stranger, deck.deck().id(), 0, 50, null, null, null)).isInstanceOf(ApiException.class);
        var page = service.cardPage(owner, deck.deck().id(), 1, 2, null, null, null);
        assertThat(page.content()).hasSize(1); assertThat(page.totalElements()).isEqualTo(3); assertThat(page.totalPages()).isEqualTo(2);
        assertThat(service.cardPage(owner, deck.deck().id(), 0, 50, "BANANA", "VOCABULARY", "NEW").content()).hasSize(1);
        assertThat(service.metadata(owner, deck.deck().id()).newCount()).isEqualTo(3);
        assertThat(page.content().getFirst().tags()).containsExactly("tag");
        assertThat(page.content().getFirst().sourceSheet()).isEqualTo("Sheet1");
    }
    @Test void editingPreservesProgressAndDeletedDeckCardsAreInaccessible() {
        UUID owner = user(); var deck = service.create(owner, request("Deck", List.of(card("one")))); UUID id = deck.cards().getFirst().id();
        new TransactionTemplate(transactions).execute(status -> {
            var progress = new AnkiCardProgress(); progress.setId(UUID.randomUUID()); progress.setUser(em.getReference(User.class, owner));
            progress.setCard(em.getReference(Card.class, id)); progress.setState(DomainTypes.AnkiState.REVIEW); progress.setDueAt(Instant.now().minusSeconds(10)); progress.setRepetitions(5); em.persist(progress); return null;
        });
        service.updateCard(owner, id, new DeckDtos.UpdateCardRequest("changed", null, null, null, null, null, null, null));
        var page = service.cardPage(owner, deck.deck().id(), 0, 50, null, null, "DUE");
        assertThat(page.content().getFirst().progress().repetitions()).isEqualTo(5);
        assertThat(service.metadata(owner, deck.deck().id()).dueCount()).isEqualTo(1);
        assertThat(service.metadata(owner, deck.deck().id()).newCount()).isZero();
        service.delete(owner, deck.deck().id());
        assertThatThrownBy(() -> service.updateCard(owner, id, new DeckDtos.UpdateCardRequest("changed", null, null, null, null, null, null, null))).isInstanceOf(ApiException.class);
    }
    @Test void invalidImportCreatesNothingAndKeyCanBeRetried() {
        UUID owner = user();
        assertThatThrownBy(() -> service.importDeck(owner, "retry", request("Deck", List.of(card("okay"),card("   "))))).isInstanceOf(ApiException.class);
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(owner)).isZero();
        assertThat(service.importDeck(owner, "retry", request("Deck", List.of(card("fixed")))).cards()).hasSize(1);
    }
    @Test void queueIsBoundedAndDeletedDeckCannotBeReviewed() {
        UUID owner = user(), stranger = user();
        var deck = service.create(owner, request("Queue", List.of(card("one"), card("two"), card("three"))));
        assertThat(anki.queue(owner, deck.deck().id(), 1)).hasSize(1);
        assertThatThrownBy(() -> anki.queue(stranger, deck.deck().id(), 1)).isInstanceOf(ApiException.class);
        service.delete(owner, deck.deck().id());
        assertThatThrownBy(() -> anki.queue(owner, deck.deck().id(), 1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> anki.review(owner, deck.cards().getFirst().id(), new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(DomainTypes.AnkiRating.GOOD, 100))).isInstanceOf(ApiException.class);
    }
}

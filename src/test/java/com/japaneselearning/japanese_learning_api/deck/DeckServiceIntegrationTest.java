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

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:deck-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=ERROR"
})
class DeckServiceIntegrationTest {
    @Autowired DeckService service;
    @Autowired com.japaneselearning.japanese_learning_api.anki.AnkiService anki;
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired AnkiCardProgressRepository progressRepository;
    @Autowired AnkiReviewLogRepository reviewLogs;
    @Autowired UserSettingsRepository settings;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired DeckGroupService groupService;

    @Test void groupsPreserveCardsAndProgressAndRejectForeignMoves() {
        UUID owner = user(), stranger = user();
        var created = service.create(owner, request("Grouped", List.of(card("one"), card("two"))));
        var cardId = created.cards().get(0).id();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var progress = new AnkiCardProgress(); progress.setId(UUID.randomUUID());
            progress.setUser(em.getReference(User.class, owner)); progress.setCard(em.getReference(Card.class, cardId));
            progress.setState(DomainTypes.AnkiState.REVIEW); progress.setDueAt(Instant.now().minusSeconds(60));
            progress.setRepetitions(7); em.persist(progress);
        });
        groupService.create(owner, new DeckGroupService.NameRequest(" N3 ", null));
        var group = groupService.list(owner).getFirst();
        assertThat(group.name()).isEqualTo("N3");
        assertThat(groupService.list(stranger)).isEmpty();
        assertThatThrownBy(() -> groupService.move(stranger, created.deck().id(), new DeckGroupService.MoveRequest(group.id(), null, 0L))).isInstanceOf(ApiException.class);
        var other = service.create(stranger, request("Other", List.of()));
        assertThatThrownBy(() -> groupService.move(stranger, other.deck().id(), new DeckGroupService.MoveRequest(group.id(), null, other.deck().version()))).isInstanceOf(ApiException.class);
        groupService.move(owner, created.deck().id(), new DeckGroupService.MoveRequest(group.id(), null, created.deck().version()));
        assertThat(service.listPage(owner, 0, 20, "position,asc", null, true).totalElements()).isZero();
        assertThat(service.listPage(owner, 0, 20, "position,asc", group.id(), true).content()).extracting(DeckDtos.DeckSummary::id).containsExactly(created.deck().id());
        var stats = groupService.list(owner).getFirst();
        assertThat(stats.cardCount()).isEqualTo(2); assertThat(stats.dueCount()).isEqualTo(1); assertThat(stats.deckCount()).isEqualTo(1);
        assertThatThrownBy(() -> groupService.move(owner, created.deck().id(), new DeckGroupService.MoveRequest(null, null, created.deck().version()))).isInstanceOf(ApiException.class);
        groupService.rename(owner, group.id(), new DeckGroupService.NameRequest("Bài học", stats.version()));
        assertThatThrownBy(() -> groupService.dissolve(owner, group.id(), stats.version())).isInstanceOf(ApiException.class);
        groupService.dissolve(owner, group.id(), groupService.list(owner).getFirst().version());
        assertThat(service.metadata(owner, created.deck().id()).groupId()).isNull();
        assertThat(service.get(owner, created.deck().id()).cards()).extracting(DeckDtos.CardResponse::id)
            .containsExactlyElementsOf(created.cards().stream().map(DeckDtos.CardResponse::id).toList());
        assertThat(service.cardPage(owner, created.deck().id(), 0, 20, "", "", "").content().getFirst().progress().repetitions()).isEqualTo(7);
    }

    @Test void groupPagingAndOrderWorkBeyondFirstPageAndSoftDeletedDecks() {
        UUID owner = user(); groupService.create(owner, new DeckGroupService.NameRequest("Many", null));
        var group = groupService.list(owner).getFirst();
        var ids = new ArrayList<UUID>();
        for (int i=0;i<23;i++) {
            var deck = service.create(owner, request("Deck " + i, List.of())); ids.add(deck.deck().id());
            groupService.move(owner, deck.deck().id(), new DeckGroupService.MoveRequest(group.id(), null, deck.deck().version()));
        }
        var page = service.listPage(owner, 1, 20, "position,asc", group.id(), true);
        assertThat(page.totalElements()).isEqualTo(23); assertThat(page.content()).hasSize(3);
        var last = service.metadata(owner, ids.getLast());
        groupService.move(owner, last.id(), new DeckGroupService.MoveRequest(group.id(), ids.getFirst(), last.version()));
        assertThat(service.listPage(owner, 0, 20, "position,asc", group.id(), true).content().getFirst().id()).isEqualTo(last.id());
        var fresh = service.metadata(owner, last.id());
        assertThatThrownBy(() -> groupService.move(owner, last.id(), new DeckGroupService.MoveRequest(null, ids.getFirst(), fresh.version()))).isInstanceOf(ApiException.class);
        assertThat(service.metadata(owner, last.id()).groupId()).isEqualTo(group.id());
        service.delete(owner, ids.getFirst());
        groupService.dissolve(owner, group.id(), groupService.list(owner).getFirst().version());
        assertThat(service.listPage(owner, 0, 20, "position,asc", null, true).totalElements()).isEqualTo(22);
        groupService.create(owner, new DeckGroupService.NameRequest("A", null));
        groupService.create(owner, new DeckGroupService.NameRequest("B", null));
        var groups = groupService.list(owner);
        groupService.reorder(owner, new DeckGroupService.OrderRequest(List.of(groups.get(1).id(), groups.get(0).id())));
        assertThat(groupService.list(owner).getFirst().id()).isEqualTo(groups.get(1).id());
        assertThatThrownBy(() -> groupService.reorder(owner, new DeckGroupService.OrderRequest(List.of(groups.getFirst().id(), groups.getFirst().id())))).isInstanceOf(ApiException.class);
    }

    @Test void deckListCountsRemainCorrectWithoutQueriesPerDeck() {
        UUID owner = user();
        var first = service.create(owner, request("First", List.of(card("one"), card("two"))));
        service.create(owner, request("Empty", List.of()));
        service.create(owner, request("Third", List.of(card("three"))));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var progress = new AnkiCardProgress();
            progress.setId(UUID.randomUUID());
            progress.setUser(em.getReference(User.class, owner));
            progress.setCard(em.getReference(Card.class, first.cards().get(0).id()));
            progress.setState(DomainTypes.AnkiState.REVIEW);
            progress.setDueAt(Instant.now().minusSeconds(60));
            progress.setLastReviewedAt(Instant.now());
            progress.setRepetitions(1);
            em.persist(progress);
        });
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        var listed = service.listPage(owner, 0, 20, "name,asc");
        System.out.println("Deck list SQL statements: " + statistics.getPrepareStatementCount());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        assertThat(listed.content()).hasSize(3);
        var result = listed.content().stream().filter(deck -> deck.id().equals(first.deck().id())).findFirst().orElseThrow();
        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.dueCount()).isEqualTo(1);
        assertThat(result.learnedCount()).isEqualTo(1);
        assertThat(result.studiedTodayCount()).isEqualTo(1);
        assertThat(result.newStartedTodayCount()).isEqualTo(1);
        assertThat(result.unresolvedCount()).isZero();
        assertThat(result.remainingTodayCount()).isEqualTo(2);
        assertThat(listed.content().stream().filter(deck -> deck.name().equals("Empty")).findFirst().orElseThrow().newCount()).isZero();
        statistics.clear();
        var withProgress = service.cardPage(owner, first.deck().id(), 0, 50, "", "", "");
        long fullStatements = statistics.getPrepareStatementCount();
        assertThat(withProgress.content().stream().filter(card -> card.id().equals(first.cards().get(0).id())).findFirst().orElseThrow().progress()).isNotNull();
        statistics.clear();
        var contentOnly = service.cardPage(owner, first.deck().id(), 0, 50, "", "", "", false);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(fullStatements - 1);
        assertThat(contentOnly.content()).allMatch(card -> card.progress() == null);
        assertThat(contentOnly.content()).extracting(DeckDtos.CardResponse::id).containsExactlyElementsOf(withProgress.content().stream().map(DeckDtos.CardResponse::id).toList());
    }

    @Test void importingManyCardsDoesNotSelectBeforeEveryInsert() {
        UUID owner = user();
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        var created = service.create(owner, request("Bulk", java.util.stream.IntStream.range(0, 200).mapToObj(i -> card("card-" + i)).toList()));
        assertThat(created.cards()).hasSize(200);
        System.out.println("Import 200 cards SQL statements prepared: " + statistics.getPrepareStatementCount());
        assertThat(statistics.getEntityInsertCount()).isEqualTo(201);
        assertThat(statistics.getPrepareStatementCount()).isLessThan(20);
        assertThat(service.cardPage(owner, created.deck().id(), 0, 200, "", "", "").content()).hasSize(200);
    }

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
        var queue = anki.queue(owner, deck.deck().id(), 1);
        assertThat(queue).hasSize(1);
        assertThat(queue.getFirst().extraData()).containsEntry("custom", "preserved");
        assertThatThrownBy(() -> anki.queue(stranger, deck.deck().id(), 1)).isInstanceOf(ApiException.class);
        service.delete(owner, deck.deck().id());
        assertThatThrownBy(() -> anki.queue(owner, deck.deck().id(), 1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> anki.review(owner, deck.cards().getFirst().id(), new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(DomainTypes.AnkiRating.GOOD, 100))).isInstanceOf(ApiException.class);
    }

    @Test void customDailyNewLimitControlsQueueAndDeckMetrics() {
        UUID owner = user();
        var deck = service.create(owner, request("No new cards", List.of(card("one"), card("two"))));
        var userSettings = new UserSettings();
        userSettings.setUser(users.findById(owner).orElseThrow());
        userSettings.setSrsDailyNewLimit(0);
        settings.saveAndFlush(userSettings);

        assertThat(anki.queue(owner, deck.deck().id(), 200)).isEmpty();
        assertThat(service.metadata(owner, deck.deck().id()).remainingTodayCount()).isZero();
    }

    @Test void reviewTelemetryIsStoredAndStaleRatingCannotAdvanceProgress() {
        UUID owner = user();
        var deck = service.create(owner, request("Timing", List.of(card("one"))));
        UUID cardId = deck.cards().getFirst().id();
        var now = java.time.Instant.now();
        var request = new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(
            UUID.randomUUID(), DomainTypes.AnkiRating.HARD, null, 5000, 1000, true,
            now.minusSeconds(10), now.minusSeconds(2), now, 0);
        var result = anki.review(owner, cardId, request);
        var log = reviewLogs.findByUserIdAndClientReviewId(owner, request.clientReviewId()).orElseThrow();
        assertThat(log.getRecallTimeMs()).isEqualTo(5000);
        assertThat(log.getDecisionTimeMs()).isEqualTo(1000);
        assertThat(log.getTimeValid()).isTrue();
        assertThat(log.getPredictedRecall()).isNull();
        assertThat(log.getSchedulerVersion()).isEqualTo("fsrs-6/java-fsrs-1.0.0");
        assertThat(result.progress().lapses()).isZero();
        var stale = new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(
            UUID.randomUUID(), DomainTypes.AnkiRating.GOOD, null, 0, -1, true, null, null, null, 0);
        assertThatThrownBy(() -> anki.review(owner, cardId, stale)).isInstanceOf(ApiException.class);
        assertThat(progressRepository.findByUserIdAndCardId(owner, cardId).orElseThrow().getRepetitions()).isEqualTo(1);
    }

    @Test void repeatedClientReviewIdIsAppliedOnlyOnce() {
        UUID owner = user();
        var deck = service.create(owner, request("Idempotent", List.of(card("one"))));
        UUID cardId = deck.cards().getFirst().id();
        UUID operation = UUID.randomUUID();
        var request = new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(
                operation, DomainTypes.AnkiRating.AGAIN, 900);
        var first = anki.review(owner, cardId, request);
        var retry = anki.review(owner, cardId, request);
        assertThat(retry.progress()).isEqualTo(first.progress());
        assertThat(progressRepository.findByUserIdAndCardId(owner, cardId).orElseThrow().getRepetitions()).isEqualTo(1);
        assertThat(reviewLogs.count()).isEqualTo(1);
    }
}

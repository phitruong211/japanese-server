package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:group-share-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class DeckGroupShareIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void mysqlVerification(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url = System.getProperty("sharing.mysql.url");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getProperty("sharing.mysql.username", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("sharing.mysql.password", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired DeckGroupShareService sharing;
    @Autowired DeckShareService deckSharing;
    @Autowired DeckGroupService groupService;
    @Autowired DeckService service;
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired CardRepository cards;
    @Autowired com.japaneselearning.japanese_learning_api.anki.AnkiService anki;
    @Autowired DeckGroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    User user() {
        var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId()+"@group-share.test");
        u.setPasswordHash("test"); u.setDisplayName("Người chia sẻ nhóm"); return users.saveAndFlush(u);
    }
    DeckGroup group(User owner) {
        var g = new DeckGroup(); g.setId(UUID.randomUUID()); g.setOwner(owner); g.setName("Nhóm N3");
        return groups.saveAndFlush(g);
    }
    DeckDtos.DeckResponse deck(User owner, DeckGroup group, String name, int count, DomainTypes.SourceType type) {
        var cards = java.util.stream.IntStream.range(0, count).mapToObj(i -> new DeckDtos.CardRequest(
            "猫 "+i, "Con mèo", "ねこ", "Ghi chú", DomainTypes.CardKind.VOCABULARY, i, "private",
            Map.<String,Object>of("sourceRef", "private.csv", "sourceSheet", "sheet", "custom", "keep"), List.of("N3"))).toList();
        var d = service.create(owner.getId(), new DeckDtos.CreateDeckRequest(name, "Mô tả", type, "private.csv", "CSV",
            DomainTypes.Visibility.PRIVATE, Map.of("version", 4), cards, type.name(), "private", null, List.of("tag")));
        groupService.move(owner.getId(), d.deck().id(), new DeckGroupService.MoveRequest(group.getId(), null, d.deck().version()));
        return d;
    }
    String enable(User owner, DeckGroup group) { return sharing.enable(owner.getId(), group.getId()).token(); }

    @Test void publicMetadataAndScopedPreviewOnlyExposeCurrentImportMembers() throws Exception {
        var owner = user(); var group = group(owner);
        var imported = deck(owner, group, "Import", 5, DomainTypes.SourceType.IMPORT);
        var manual = deck(owner, group, "Manual", 3, DomainTypes.SourceType.MANUAL);
        var foreignGroup = group(owner); var outside = deck(owner, foreignGroup, "Outside", 1, DomainTypes.SourceType.IMPORT);
        assertThat(sharing.status(owner.getId(), group.getId())).isEqualTo(new DeckGroupShareService.Link(null, 1, 1, 5));
        String token = enable(owner, group);
        assertThat(enable(owner, group)).isEqualTo(token);
        mvc.perform(get("/api/v1/shared-deck-groups/"+token)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
            .andExpect(jsonPath("$.deckCount").value(1)).andExpect(jsonPath("$.excludedDeckCount").value(1))
            .andExpect(jsonPath("$.cardCount").value(5)).andExpect(jsonPath("$.decks[0].name").value("Import"))
            .andExpect(jsonPath("$.ownerId").doesNotExist()).andExpect(jsonPath("$.decks[0].shareToken").doesNotExist());
        mvc.perform(get("/api/v1/shared-deck-groups/"+token+"/decks/"+imported.deck().id())).andExpect(status().isOk())
            .andExpect(jsonPath("$.previewCount").value(3)).andExpect(jsonPath("$.cards.length()").value(3))
            .andExpect(jsonPath("$.cards[0].notes").doesNotExist()).andExpect(jsonPath("$.sourceRef").doesNotExist());
        assertThat(deckSharing.status(owner.getId(), imported.deck().id()).token()).isNull();
        for (var id : List.of(manual.deck().id(), outside.deck().id()))
            mvc.perform(get("/api/v1/shared-deck-groups/"+token+"/decks/"+id)).andExpect(status().isNotFound());
        groupService.move(owner.getId(), imported.deck().id(), new DeckGroupService.MoveRequest(null, null,
                service.get(owner.getId(), imported.deck().id()).deck().version()));
        assertThat(sharing.preview(token, 0).decks()).isEmpty();
        assertThatThrownBy(() -> sharing.previewDeck(token, imported.deck().id(), 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sharing.copy(user().getId(), token)).isInstanceOf(ApiException.class);
    }

    @Test void ownershipAuthRevokeRotationAndDissolveAreEnforced() throws Exception {
        var owner = user(); var stranger = user(); var group = group(owner);
        deck(owner, group, "Import", 1, DomainTypes.SourceType.IMPORT);
        String token = enable(owner, group);
        mvc.perform(post("/api/v1/shared-deck-groups/"+token+"/copy")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/deck-groups/"+group.getId()+"/share")).andExpect(status().isUnauthorized());
        String bearer = "Bearer "+jwt.createAccessToken(stranger);
        mvc.perform(put("/api/v1/deck-groups/"+group.getId()+"/share").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/deck-groups/"+group.getId()+"/share").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/shared-deck-groups/"+token+"/copy").header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.deckIds.length()").value(1));
        sharing.revoke(owner.getId(), group.getId());
        assertThatThrownBy(() -> sharing.preview(token, 0)).isInstanceOf(ApiException.class);
        String next = enable(owner, group); assertThat(next).isNotEqualTo(token);
        assertThat(sharing.copy(stranger.getId(), next).alreadySaved()).isTrue();
        groupService.dissolve(owner.getId(), group.getId(), groups.findById(group.getId()).orElseThrow().getVersion());
        assertThatThrownBy(() -> sharing.preview(next, 0)).isInstanceOf(ApiException.class);
        for (String invalid : List.of("bad", UUID.randomUUID().toString()))
            assertThatThrownBy(() -> sharing.preview(invalid, 0)).isInstanceOf(ApiException.class);
    }

    @Test void copyIsIndependentOrderedIdempotentAndDoesNotRelocateStandaloneDecks() {
        var owner = user(); var recipient = user(); var group = group(owner);
        var first = deck(owner, group, "First", 2, DomainTypes.SourceType.IMPORT);
        deck(owner, group, "Excluded", 2, DomainTypes.SourceType.MANUAL);
        deck(owner, group, "Second", 1, DomainTypes.SourceType.IMPORT);
        var originalCard = cards.findById(first.cards().getFirst().id()).orElseThrow();
        originalCard.setDictionaryIdentity("jmdict:test"); originalCard.setDictionaryProvenance("JMdict attribution"); cards.saveAndFlush(originalCard);
        anki.review(owner.getId(), originalCard.getId(), new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(DomainTypes.AnkiRating.GOOD, 100));
        UUID standalone = deckSharing.copy(recipient.getId(), deckSharing.enable(owner.getId(), first.deck().id()).token()).deckId();
        String token = enable(owner, group);
        var saved = sharing.copy(recipient.getId(), token);
        assertThat(saved.alreadySaved()).isFalse(); assertThat(saved.deckIds()).hasSize(2).doesNotContain(standalone);
        assertThat(sharing.copy(owner.getId(), token).groupId()).isEqualTo(group.getId());
        assertThat(decks.findById(standalone).orElseThrow().getGroupId()).isNull();
        assertThat(saved.deckIds().stream().map(id -> service.get(recipient.getId(), id).deck().name()).toList()).containsExactly("First", "Second");
        var copy = service.get(recipient.getId(), saved.deckIds().getFirst());
        assertThat(copy.deck().templateConfig()).isEqualTo(first.deck().templateConfig());
        assertThat(copy.deck().tags()).containsExactly("tag"); assertThat(copy.deck().sourceRef()).isNull();
        assertThat(copy.cards()).allMatch(c -> c.progress() == null);
        var copiedCard = cards.findById(copy.cards().getFirst().id()).orElseThrow();
        assertThat(copiedCard.getDictionaryIdentity()).isEqualTo("jmdict:test");
        assertThat(copiedCard.getDictionaryProvenance()).isEqualTo("JMdict attribution");
        assertThat(copy.cards().getFirst().extraData()).containsEntry("dictionaryRef", "JMdict attribution");

        assertThat(copy.cards().getFirst().notes()).isEqualTo("Ghi chú");
        assertThat(copy.cards().getFirst().extraData()).containsEntry("custom", "keep").doesNotContainKeys("sourceRef", "sourceSheet");
        assertThat(deckSharing.status(recipient.getId(), copy.deck().id()).token()).isNull();
        assertThat(sharing.status(recipient.getId(), saved.groupId()).token()).isNull();
        // User edits their copy; retry must not repopulate it or move this deck back.
        groupService.move(recipient.getId(), copy.deck().id(), new DeckGroupService.MoveRequest(null, null, copy.deck().version()));
        service.delete(recipient.getId(), saved.deckIds().getLast());
        var retry = sharing.copy(recipient.getId(), token);
        assertThat(retry.groupId()).isEqualTo(saved.groupId()); assertThat(retry.deckIds()).isEmpty(); assertThat(retry.alreadySaved()).isTrue();
        groupService.dissolve(recipient.getId(), saved.groupId(), groups.findById(saved.groupId()).orElseThrow().getVersion());
        var fresh = sharing.copy(recipient.getId(), token);
        assertThat(fresh.groupId()).isNotEqualTo(saved.groupId()); assertThat(fresh.deckIds()).doesNotContainAnyElementsOf(saved.deckIds());
        assertThat(decks.findById(copy.deck().id()).orElseThrow().getGroupId()).isNull();
    }

    @Test void metadataPagingAndEmptyGroupsAreBounded() {
        var owner = user(); var group = group(owner);
        assertThatThrownBy(() -> enable(owner, group)).isInstanceOf(ApiException.class);
        deck(owner, group, "Manual", 0, DomainTypes.SourceType.MANUAL);
        assertThatThrownBy(() -> enable(owner, group)).isInstanceOf(ApiException.class);
        for (int i = 0; i < 21; i++) deck(owner, group, "Import "+i, 0, DomainTypes.SourceType.IMPORT);
        String token = enable(owner, group);
        assertThat(sharing.preview(token, 0).decks()).hasSize(20); assertThat(sharing.preview(token, 1).decks()).hasSize(1);
        assertThat(sharing.preview(token, Integer.MAX_VALUE).decks()).isEmpty();
        assertThatThrownBy(() -> sharing.preview(token, -1)).isInstanceOf(ApiException.class);
    }

    @Test void excessiveDeckCountRejectsWithoutPartialCopies() {
        var owner = user(); var recipient = user(); var group = group(owner);
        for (int i = 0; i < 101; i++) deck(owner, group, "Import "+i, 0, DomainTypes.SourceType.IMPORT);
        String token = enable(owner, group);
        assertThatThrownBy(() -> sharing.copy(recipient.getId(), token)).isInstanceOf(ApiException.class).hasMessageContaining("100");
        assertThat(groups.findAllByOwnerIdOrderByPositionAscIdAsc(recipient.getId())).isEmpty();
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(recipient.getId())).isZero();
    }

    @Test void aggregateCardLimitRejectsBeforeAnyTargetIsCreated() {
        var owner = user(); var recipient = user(); var group = group(owner);
        deck(owner, group, "A", 10_001, DomainTypes.SourceType.IMPORT);
        deck(owner, group, "B", 10_000, DomainTypes.SourceType.IMPORT);
        String token = enable(owner, group);
        assertThatThrownBy(() -> sharing.copy(recipient.getId(), token)).isInstanceOf(ApiException.class).hasMessageContaining("20.000");
        assertThat(groups.findAllByOwnerIdOrderByPositionAscIdAsc(recipient.getId())).isEmpty();
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(recipient.getId())).isZero();
    }

    @Test void concurrentRetriesAndReciprocalCopiesUseConsistentLocks() throws Exception {
        var a = user(); var b = user(); var ga = group(a); var gb = group(b);
        deck(a, ga, "A", 2, DomainTypes.SourceType.IMPORT); deck(b, gb, "B", 2, DomainTypes.SourceType.IMPORT);
        String ta = enable(a, ga), tb = enable(b, gb);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var start = new CountDownLatch(1);
            Callable<DeckGroupShareService.CopyResult> ab = () -> { start.await(); return sharing.copy(b.getId(), ta); };
            var one = executor.submit(ab); var two = executor.submit(ab);
            var reverse = executor.submit(() -> { start.await(); return sharing.copy(a.getId(), tb); }); start.countDown();
            assertThat(one.get(30, TimeUnit.SECONDS).groupId()).isEqualTo(two.get(30, TimeUnit.SECONDS).groupId());
            assertThat(reverse.get(30, TimeUnit.SECONDS).deckIds()).hasSize(1);
        }
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(a.getId())).isEqualTo(2);
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(b.getId())).isEqualTo(2);
    }
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "sharing.mysql.url", matches = ".+")
    @Test void currentReadsObserveConcurrentCopiesAndMembershipAfterOldSnapshot() throws Exception {
        var owner = user(); var recipient = user(); var group = group(owner);
        deck(owner, group, "First", 1, DomainTypes.SourceType.IMPORT);
        String token = enable(owner, group);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                assertThat(groups.findByShareToken(token)).isPresent();
                try {
                    executor.submit(() -> deck(owner, group, "Added later", 1, DomainTypes.SourceType.IMPORT)).get(20, TimeUnit.SECONDS);
                } catch (Exception ex) { throw new AssertionError(ex); }
                assertThat(sharing.copy(recipient.getId(), token).deckIds()).hasSize(2);
            });
            var nextRecipient = user();
            transaction.executeWithoutResult(status -> {
                assertThat(groups.findByShareToken(token)).isPresent();
                try {
                    var committed = executor.submit(() -> sharing.copy(nextRecipient.getId(), token)).get(20, TimeUnit.SECONDS);
                    var retry = sharing.copy(nextRecipient.getId(), token);
                    assertThat(retry.groupId()).isEqualTo(committed.groupId());
                    assertThat(retry.deckIds()).hasSize(2); assertThat(retry.alreadySaved()).isTrue();
                } catch (Exception ex) { throw new AssertionError(ex); }
            });
        }
    }

}

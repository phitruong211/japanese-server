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
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:share-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class DeckShareIntegrationTest {
    // Optional disposable MySQL run: validates the real migrations and default isolation.
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
    @Autowired DeckShareService sharing;
    @Autowired DeckService service;
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.japaneselearning.japanese_learning_api.anki.AnkiService anki;

    User user() {
        var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId()+"@share.test");
        u.setPasswordHash("test"); u.setDisplayName("Người chia sẻ"); return users.saveAndFlush(u);
    }
    DeckDtos.DeckResponse deck(User owner, int size) {
        var cards = java.util.stream.IntStream.range(0, size).mapToObj(i -> new DeckDtos.CardRequest(
            "猫 " + i, "Con mèo", "ねこ", "Ghi chú", DomainTypes.CardKind.VOCABULARY, i, "external-"+i,
            Map.<String, Object>of("backReading", "reading", "hanViet", "Miêu", "custom", "kept",
                "sourceRef", "private-file.csv", "sourceSheet", "Private sheet"), List.of("N3"))).toList();
        return service.create(owner.getId(), new DeckDtos.CreateDeckRequest("Bộ của tôi", "Mô tả", DomainTypes.SourceType.IMPORT,
            "private-file.csv", "CSV", DomainTypes.Visibility.PRIVATE, Map.of("version", 4), cards, "IMPORT", "private-path", null, List.of("deck-tag")));
    }

    @Test void publicPreviewIsBoundedAndDoesNotExposePrivateMetadata() throws Exception {
        var owner = user(); var source = deck(owner, 15); var id = source.deck().id();
        assertThat(sharing.status(owner.getId(), id).token()).isNull();
        String token = sharing.enable(owner.getId(), id).token();
        assertThat(sharing.enable(owner.getId(), id).token()).isEqualTo(token);
        mvc.perform(get("/api/v1/shared-decks/"+token)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.cardCount").value(15)).andExpect(jsonPath("$.previewCount").value(8))
            .andExpect(jsonPath("$.cards.length()").value(8))
            .andExpect(jsonPath("$.author").value(owner.getDisplayName()))
            .andExpect(jsonPath("$.sourceName").doesNotExist()).andExpect(jsonPath("$.ownerId").doesNotExist())
            .andExpect(jsonPath("$.cards[0].progress").doesNotExist()).andExpect(jsonPath("$.cards[0].notes").doesNotExist());
        mvc.perform(post("/api/v1/shared-decks/"+token+"/copy")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/decks/"+id+"/share")).andExpect(status().isUnauthorized());
        var stranger = user(); String bearer = "Bearer " + jwt.createAccessToken(stranger);
        mvc.perform(put("/api/v1/decks/"+id+"/share").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/decks/"+id+"/share").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/shared-decks/"+token+"/copy").header("Authorization", bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.alreadySaved").value(false));
        mvc.perform(delete("/api/v1/decks/"+id+"/share").header("Authorization", "Bearer "+jwt.createAccessToken(owner)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/shared-decks/"+token)).andExpect(status().isNotFound());
    }

    @Test void copyPreservesContentAndTemplateButStartsIndependentProgress() {
        var owner = user(); var recipient = user(); var source = deck(owner, 2);
        anki.review(owner.getId(), source.cards().getFirst().id(), new com.japaneselearning.japanese_learning_api.anki.AnkiDtos.ReviewRequest(DomainTypes.AnkiRating.GOOD, 100));
        String token = sharing.enable(owner.getId(), source.deck().id()).token();
        var saved = sharing.copy(recipient.getId(), token);
        assertThat(saved.alreadySaved()).isFalse();
        assertThat(sharing.copy(recipient.getId(), token)).isEqualTo(new DeckShareService.CopyResult(saved.deckId(), true));
        assertThat(sharing.copy(owner.getId(), token).deckId()).isEqualTo(source.deck().id());
        var copy = service.get(recipient.getId(), saved.deckId());
        assertThat(copy.deck().templateConfig()).isEqualTo(source.deck().templateConfig());
        assertThat(copy.deck().tags()).containsExactly("deck-tag");
        assertThat(copy.deck().visibility()).isEqualTo(DomainTypes.Visibility.PRIVATE);
        assertThat(copy.deck().sourceRef()).isNull();
        assertThat(sharing.status(recipient.getId(), saved.deckId()).token()).isNull();
        assertThat(copy.cards()).hasSize(2).allMatch(c -> c.progress() == null);
        assertThat(copy.cards().getFirst().extraData()).containsEntry("hanViet", "Miêu").containsEntry("tags", List.of("N3"));
        assertThat(copy.cards().getFirst().extraData()).doesNotContainKeys("sourceRef", "sourceSheet");
        assertThat(copy.cards().getFirst().sourceRef()).isNull();
        assertThat(service.get(owner.getId(), source.deck().id()).cards().getFirst().extraData()).containsEntry("sourceRef", "private-file.csv");
        assertThat(copy.cards().getFirst().notes()).isEqualTo("Ghi chú");
        assertThat(copy.cards().getFirst().id()).isNotEqualTo(source.cards().getFirst().id());
        service.updateCard(recipient.getId(), copy.cards().getFirst().id(), new DeckDtos.UpdateCardRequest("犬", null, null, null, null, null, null, null));
        assertThat(service.get(owner.getId(), source.deck().id()).cards().getFirst().front()).isEqualTo("猫 0");
        sharing.revoke(owner.getId(), source.deck().id());
        assertThat(service.get(recipient.getId(), saved.deckId()).cards()).hasSize(2);
        assertThatThrownBy(() -> sharing.copy(recipient.getId(), token)).isInstanceOf(ApiException.class);
        String nextToken = sharing.enable(owner.getId(), source.deck().id()).token();
        assertThat(nextToken).isNotEqualTo(token);
        assertThat(sharing.copy(recipient.getId(), nextToken).deckId()).isEqualTo(saved.deckId());
        service.delete(recipient.getId(), saved.deckId());
        assertThat(sharing.copy(recipient.getId(), nextToken).deckId()).isNotEqualTo(saved.deckId());
        service.delete(owner.getId(), source.deck().id());
        assertThatThrownBy(() -> sharing.preview(nextToken, 0)).isInstanceOf(ApiException.class);
    }

    @Test void concurrentRetriesCreateOnlyOneCopy() throws Exception {
        var owner = user(); var recipient = user(); var source = deck(owner, 3);
        String token = sharing.enable(owner.getId(), source.deck().id()).token();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<DeckShareService.CopyResult> action = () -> { start.await(); return sharing.copy(recipient.getId(), token); };
            var a = executor.submit(action); var b = executor.submit(action); start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS).deckId()).isEqualTo(b.get(20, TimeUnit.SECONDS).deckId());
        }
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(recipient.getId())).isEqualTo(1);
    }

    // H2 does not implement MySQL's current-read semantics for FOR UPDATE in REPEATABLE READ.
    // Run this engine-specific regression with the disposable MySQL verification profile.
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "sharing.mysql.url", matches = ".+")
    @Test void retrySeesCopyCommittedAfterItsRepeatableReadSnapshot() throws Exception {
        var owner = user(); var recipient = user(); var source = deck(owner, 2);
        String token = sharing.enable(owner.getId(), source.deck().id()).token();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                // Establish an old snapshot, then let another request save and commit.
                assertThat(decks.findByShareTokenAndDeletedAtIsNull(token)).isPresent();
                DeckShareService.CopyResult first;
                try { first = executor.submit(() -> sharing.copy(recipient.getId(), token)).get(20, TimeUnit.SECONDS); }
                catch (Exception e) { throw new AssertionError(e); }
                assertThat(sharing.copy(recipient.getId(), token))
                    .isEqualTo(new DeckShareService.CopyResult(first.deckId(), true));
            });
        }
        assertThat(decks.countByOwnerIdAndDeletedAtIsNull(recipient.getId())).isEqualTo(1);
    }

    @Test void invalidLinksAndNonImportedDecksCannotBeShared() {
        var owner = user(); var source = deck(owner, 0);
        var entity = decks.findById(source.deck().id()).orElseThrow(); entity.setSourceType(DomainTypes.SourceType.MANUAL); decks.saveAndFlush(entity);
        assertThatThrownBy(() -> sharing.enable(owner.getId(), source.deck().id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sharing.preview("invalid", 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sharing.preview(UUID.randomUUID().toString(), 0)).isInstanceOf(ApiException.class);
    }

    @Test void publicPagingExposesOnlyTheFirstHalfEvenOnTheLastPage() throws Exception {
        var owner = user(); var source = deck(owner, 203);
        String token = sharing.enable(owner.getId(), source.deck().id()).token();
        var first = sharing.preview(token, 0);
        var second = sharing.preview(token, 1);
        var last = sharing.preview(token, 2);
        assertThat(first.previewCount()).isEqualTo(102);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.cards()).hasSize(50);
        assertThat(second.cards()).hasSize(50);
        assertThat(last.cards()).extracting(DeckShareService.PreviewCard::front).containsExactly("猫 100", "猫 101");
        assertThat(sharing.preview(token, 3).cards()).isEmpty();
        assertThat(sharing.preview(token, Integer.MAX_VALUE).cards()).isEmpty();
        mvc.perform(get("/api/v1/shared-decks/"+token).param("page", "2").param("size", "20000"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(2));
        mvc.perform(get("/api/v1/shared-decks/"+token).param("page", "-1")).andExpect(status().isBadRequest());
        service.deleteCard(owner.getId(), source.cards().getFirst().id());
        assertThat(sharing.preview(token, 0).cardCount()).isEqualTo(202);
        assertThat(sharing.preview(token, 2).cards()).hasSize(1);
    }

    @Test void smallDecksRoundThePreviewUpAndEmptyDecksRemainEmpty() {
        var owner = user();
        for (int count = 0; count <= 3; count++) {
            var source = deck(owner, count);
            String token = sharing.enable(owner.getId(), source.deck().id()).token();
            var preview = sharing.preview(token, 0);
            assertThat(preview.previewCount()).isEqualTo((count + 1) / 2);
            assertThat(preview.cards()).hasSize((count + 1) / 2);
        }
    }
}

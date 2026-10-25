package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:note-type-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
class DeckNoteTypeIntegrationTest extends com.japaneselearning.japanese_learning_api.MySqlVerification {
    @Autowired DeckService service;
    @Autowired DeckShareService sharing;
    @Autowired UserRepository users;
    @Autowired CardRepository cards;
    @Autowired com.japaneselearning.japanese_learning_api.anki.AnkiService anki;

    private static List<Map<String, Object>> standardFields() {
        return List.of(
            Map.of("key", "front", "label", "Mặt trước", "type", "ruby", "required", true),
            Map.of("key", "frontReading", "label", "Cách đọc", "type", "kana", "for", "front"),
            Map.of("key", "back", "label", "Mặt sau", "type", "ruby", "required", true),
            Map.of("key", "notes", "label", "Ghi chú", "type", "text"),
            Map.of("key", "level", "label", "Cấp độ", "type", "text"));
    }
    private static Map<String, Object> template() {
        return Map.of("version", 5, "style", Map.of("theme", "dark"), "noteTypes", List.of(Map.of(
            "id", "jlpt", "name", "JLPT", "kind", "GRAMMAR", "fields", standardFields(),
            "front", Map.of("show", List.of("front")), "back", Map.of("show", List.of("back", "notes", "level")))));
    }
    private UUID user() {
        var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId() + "@types.test"); u.setDisplayName("Người dùng"); u.setPasswordHash("test");
        return users.saveAndFlush(u).getId();
    }
    private DeckDtos.CreateDeckRequest request(Map<String, Object> template, List<DeckDtos.CardRequest> cards) {
        return new DeckDtos.CreateDeckRequest("Bộ loại thẻ", null, DomainTypes.SourceType.IMPORT, "x.json", "JSON", null, template, cards, "IMPORT", "x.json", null, List.of());
    }
    private DeckDtos.CardRequest fieldsCard(String noteType, Map<String, Object> fields) {
        return new DeckDtos.CardRequest(null, null, null, null, null, null, null, null, List.of("N3"), noteType, fields);
    }

    @Test void fieldsAreStoredAndLegacyColumnsAreDerived() {
        var owner = user();
        var created = service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of(
            "front", "～ばかり", "back", "chỉ toàn", "level", "N3", "notes", "Hay dùng với ている",
            "frontReading", "  ばかり ")))));
        var card = created.cards().getFirst();
        assertThat(card.noteType()).isEqualTo("jlpt");
        assertThat(card.fields()).containsEntry("level", "N3").containsEntry("frontReading", "ばかり");
        assertThat(card.front()).isEqualTo("～ばかり");
        assertThat(card.reading()).isEqualTo("ばかり");
        assertThat(card.notes()).isEqualTo("Hay dùng với ている");
        assertThat(card.kind()).isEqualTo(DomainTypes.CardKind.GRAMMAR);
        assertThat(created.deck().templateConfig()).containsEntry("version", 5);
    }

    @Test void furiganaMarkupBecomesCleanColumnsAndReading() {
        var owner = user();
        var card = service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of("front", "勉強[べんきょう]する", "back", "học"))))).cards().getFirst();
        assertThat(card.fields()).containsEntry("front", "勉強[べんきょう]する");
        assertThat(card.front()).isEqualTo("勉強する");
        assertThat(card.reading()).isEqualTo("べんきょうする");
    }

    @Test void invalidTemplatesAndFieldsAreRejected() {
        var owner = user();
        var badType = new HashMap<>(template());
        badType.put("noteTypes", List.of(Map.of("id", "Bad Id")));
        assertThatThrownBy(() -> service.create(owner, request(badType, List.of()))).isInstanceOf(ApiException.class);
        var legacyWithTypes = new HashMap<String, Object>(Map.of("version", 4, "noteTypes", template().get("noteTypes")));
        assertThatThrownBy(() -> service.create(owner, request(legacyWithTypes, List.of()))).isInstanceOf(ApiException.class);
        var extraRoot = new HashMap<>(template()); extraRoot.put("front", Map.of());
        assertThatThrownBy(() -> service.create(owner, request(extraRoot, List.of()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(owner, request(template(), List.of(fieldsCard("nope", Map.of("front", "a", "back", "b")))))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of("front", "a")))))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of("front", "a", "back", "b", "ghost", "x")))))).isInstanceOf(ApiException.class);
        assertThat(users.count()).isPositive();
    }

    @Test void builtInNoteTypesWorkOnDecksWithoutAV5Template() {
        var owner = user();
        var created = service.create(owner, request(Map.of("version", 4), List.of(fieldsCard("kanji", Map.of("front", "山", "back", "núi", "hanViet", "Sơn")))));
        assertThat(created.cards().getFirst().kind()).isEqualTo(DomainTypes.CardKind.KANJI);
        assertThat(created.cards().getFirst().fields()).containsEntry("hanViet", "Sơn");
    }

    @Test void updatingFieldsReprojectsAndLegacyEditsKeepFieldsInSync() {
        var owner = user();
        var created = service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of("front", "a", "back", "b", "level", "N3")))));
        var id = created.cards().getFirst().id();
        var updated = service.updateCard(owner, id, new DeckDtos.UpdateCardRequest(null, null, null, null, null, null, null, null, null, Map.of("front", "c", "back", "d", "notes", "n")));
        assertThat(updated.front()).isEqualTo("c");
        assertThat(updated.notes()).isEqualTo("n");
        assertThat(updated.fields()).doesNotContainKey("level").containsEntry("back", "d");
        var legacy = service.updateCard(owner, id, new DeckDtos.UpdateCardRequest("e", null, "よみ", "", null, null, null, null, null, null));
        assertThat(legacy.fields()).containsEntry("front", "e").containsEntry("frontReading", "よみ").doesNotContainKey("notes");
        assertThat(cards.findById(id).orElseThrow().getFields()).containsEntry("front", "e");
    }

    @Test void legacyCardsStillWorkWithoutFields() {
        var owner = user();
        var created = service.create(owner, request(Map.of("version", 4), List.of(new DeckDtos.CardRequest("猫", "mèo", "ねこ", null, DomainTypes.CardKind.VOCABULARY, null, null, Map.of("backReading", "x"), null))));
        var card = created.cards().getFirst();
        assertThat(card.fields()).isNull();
        assertThat(card.noteType()).isNull();
        assertThat(card.extraData()).containsEntry("backReading", "x");
    }

    @SuppressWarnings("unchecked")
    @Test void styleAtEveryTierIsAcceptedKeptAndCheckedStrictly() {
        var owner = user();
        var styled = new HashMap<>(template());
        styled.put("style", Map.of("theme", "dark", "colors", Map.of("bg", "#101010", "fg", "#f5f5f5"), "font", Map.of("family", "kleeOne", "size", 32)));
        var card = new DeckDtos.CardRequest(null, null, null, null, null, null, null,
            Map.of("style", Map.of("colors", Map.of("accent", "#ff8800"), "font", Map.of("bold", true))), List.of(), "jlpt", Map.of("front", "a", "back", "b"));
        var created = service.create(owner, request(styled, List.of(card)));
        assertThat(((Map<String, Object>) created.deck().templateConfig().get("style"))).containsEntry("theme", "dark");
        assertThat(created.cards().getFirst().extraData()).containsKey("style");
        assertThat(((Map<String, Object>) created.cards().getFirst().extraData().get("style"))).containsKey("colors");
        for (Object bad : List.of(Map.of("colors", Map.of("bg", "red")), Map.of("font", Map.of("size", 100)), Map.of("zoom", 2))) {
            var rejected = new DeckDtos.CardRequest(null, null, null, null, null, null, null, Map.of("style", bad), List.of(), "jlpt", Map.of("front", "a", "back", "b"));
            assertThatThrownBy(() -> service.create(owner, request(template(), List.of(rejected)))).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.updateCard(owner, created.cards().getFirst().id(), new DeckDtos.UpdateCardRequest(null, null, null, null, null, null, Map.of("style", bad), null))).isInstanceOf(ApiException.class);
        }
        var badTemplate = new HashMap<>(template());
        badTemplate.put("style", Map.of("colors", Map.of("bg", "url(x)")));
        assertThatThrownBy(() -> service.create(owner, request(badTemplate, List.of()))).isInstanceOf(ApiException.class);
        var source = created.deck().id(); var recipient = user();
        String token = sharing.enable(owner, source).token();
        var copy = service.get(recipient, sharing.copy(recipient, token).deckId());
        assertThat(copy.cards().getFirst().extraData()).containsKey("style");
        assertThat(copy.deck().templateConfig()).isEqualTo(created.deck().templateConfig());
    }

    @SuppressWarnings("unchecked")
    @Test void uploadedFontsStayWithTheirOwnerWhenADeckIsShared() {
        var owner = user(); var recipient = user();
        String custom = "custom:123e4567-e89b-12d3-a456-426614174000";
        var type = new HashMap<>((Map<String, Object>) ((List<?>) template().get("noteTypes")).getFirst());
        type.put("style", Map.of("font", Map.of("family", custom, "size", 30)));
        type.put("front", Map.of("show", List.of("front"), "style", Map.of("font", Map.of("family", custom))));
        var fields = new ArrayList<Map<String, Object>>((List<Map<String, Object>>) type.get("fields"));
        fields.set(0, new HashMap<>(fields.get(0)) {{ put("style", Map.of("font", Map.of("family", custom), "colors", Map.of("fg", "#112233"))); }});
        type.put("fields", fields);
        var config = Map.<String, Object>of("version", 5, "style", Map.of("font", Map.of("family", custom), "colors", Map.of("bg", "#fff7e6")), "noteTypes", List.of(type));
        var card = new DeckDtos.CardRequest(null, null, null, null, null, null, null,
            Map.of("style", Map.of("font", Map.of("family", custom), "colors", Map.of("fg", "#ff0000"))), List.of(), "jlpt", Map.of("front", "a", "back", "b"));
        var source = service.create(owner, request(config, List.of(card)));
        assertThat(source.deck().templateConfig().toString()).contains(custom);
        String token = sharing.enable(owner, source.deck().id()).token();
        var copy = service.get(recipient, sharing.copy(recipient, token).deckId());
        assertThat(copy.deck().templateConfig().toString()).doesNotContain("custom:");
        assertThat(copy.cards().getFirst().extraData().toString()).doesNotContain("custom:");
        // Everything else about the look is kept.
        assertThat(copy.deck().templateConfig().toString()).contains("#fff7e6", "#112233", "size=30");
        assertThat(copy.cards().getFirst().extraData().toString()).contains("#ff0000");
        // The owner's deck still names its font.
        assertThat(service.get(owner, source.deck().id()).deck().templateConfig().toString()).contains(custom);
    }

    @Test void sharedCopiesKeepTheNoteTypeFieldsAndTemplate() {
        var owner = user(); var recipient = user();
        var source = service.create(owner, request(template(), List.of(fieldsCard("jlpt", Map.of("front", "a", "back", "b", "level", "N2")))));
        String token = sharing.enable(owner, source.deck().id()).token();
        var copy = service.get(recipient, sharing.copy(recipient, token).deckId());
        assertThat(copy.deck().templateConfig()).isEqualTo(source.deck().templateConfig());
        assertThat(copy.cards().getFirst().noteType()).isEqualTo("jlpt");
        assertThat(copy.cards().getFirst().fields()).containsEntry("level", "N2");
        var queue = anki.queue(recipient, copy.deck().id(), 10);
        assertThat(queue.getFirst().fields()).containsEntry("level", "N2");
        assertThat(queue.getFirst().noteType()).isEqualTo("jlpt");
    }
}

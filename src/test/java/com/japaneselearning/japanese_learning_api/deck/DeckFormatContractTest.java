package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the cases in resources/deck-format/cases.json, which the frontend runs too (N3/tests/deckFormat.test.mjs). */
class DeckFormatContractTest {
    @SuppressWarnings("unchecked")
    private static final Map<String, Object> CASES = readCases();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readCases() {
        try (var in = DeckFormatContractTest.class.getResourceAsStream("/deck-format/cases.json")) {
            return new ObjectMapper().readValue(in, Map.class);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    private static Object expand(Object value) {
        if (value instanceof Map<?, ?> m && m.containsKey("$repeat")) return String.valueOf(m.get("$repeat")).repeat(((Number) m.get("times")).intValue());
        if (value instanceof Map<?, ?> m) { Map<String, Object> out = new LinkedHashMap<>(); m.forEach((k, v) -> out.put((String) k, expand(v))); return out; }
        if (value instanceof List<?> l) return l.stream().map(DeckFormatContractTest::expand).toList();
        return value;
    }

    @TestFactory
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> noteTypes() {
        return ((List<Map<String, Object>>) CASES.get("noteTypes")).stream().map(item -> DynamicTest.dynamicTest("note type: " + item.get("name"), () -> {
            if ("ok".equals(item.get("expect"))) assertThat(DeckFormat.parseNoteTypes(List.of(item.get("noteType")))).hasSize(1);
            else assertThatThrownBy(() -> DeckFormat.parseNoteTypes(List.of(item.get("noteType")))).isInstanceOf(ApiException.class);
        }));
    }

    @TestFactory
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> cards() {
        var type = DeckFormat.parseNoteTypes(List.of(CASES.get("standardNoteType"))).getFirst();
        return ((List<Map<String, Object>>) CASES.get("cards")).stream().map(item -> DynamicTest.dynamicTest("card: " + item.get("name"), () -> {
            Map<String, Object> fields = (Map<String, Object>) expand(item.get("fields"));
            if ("ok".equals(item.get("expect"))) {
                var valid = DeckFormat.validateFields(type, fields);
                assertThat(valid.front()).isEqualTo(item.get("front"));
                assertThat(valid.reading()).isEqualTo(item.get("reading"));
            } else assertThatThrownBy(() -> DeckFormat.validateFields(type, fields)).isInstanceOf(ApiException.class);
        }));
    }

    @Test void unknownFieldsAreRejectedByTheApi() {
        var type = DeckFormat.builtin("vocab");
        assertThatThrownBy(() -> DeckFormat.validateFields(type, Map.of("front", "a", "back", "b", "mystery", "x"))).isInstanceOf(ApiException.class);
    }

    @Test void templateSizeLimitsOnNoteTypes() {
        var fields = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 25; i++) fields.add(Map.of("key", i == 0 ? "front" : i == 1 ? "back" : "f" + i, "label", "x", "type", "text"));
        Map<String, Object> type = Map.of("id", "big", "name", "Big", "fields", fields, "front", Map.of("show", List.of("front")), "back", Map.of("show", List.of("back")));
        assertThatThrownBy(() -> DeckFormat.parseNoteTypes(List.of(type))).isInstanceOf(ApiException.class);
        var many = Collections.nCopies(17, Map.of("id", "a"));
        assertThatThrownBy(() -> DeckFormat.parseNoteTypes(many)).isInstanceOf(ApiException.class);
    }

    @Test void styleIsStrict() {
        for (Object bad : List.of(Map.of("theme", "neon"), Map.of("colors", Map.of("bg", "red")), Map.of("font", Map.of("size", 99)),
                Map.of("font", Map.of("family", "a b")), Map.of("align", "right"), Map.of("zoom", 2)))
            assertThatThrownBy(() -> DeckFormat.validateStyle(bad, "style")).isInstanceOf(ApiException.class);
        DeckFormat.validateStyle(Map.of("theme", "dark", "colors", Map.of("bg", "#fff"), "font", Map.of("family", "custom:123e4567-e89b-12d3-a456-426614174000", "size", 24)), "style");
    }
}

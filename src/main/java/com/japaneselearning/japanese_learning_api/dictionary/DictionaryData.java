package com.japaneselearning.japanese_learning_api.dictionary;

import java.util.List;

public final class DictionaryData {
    private DictionaryData() {}
    public record Reading(String text, List<String> writtenForms, boolean kanaOnly) {}
    public record Gloss(String text, String language) {}
    public record Sense(String id, List<String> partsOfSpeech, List<String> writtenForms,
                        List<String> readings, List<Gloss> glosses, List<String> notes) {}
    public record Word(String key, String version, List<String> writtenForms, List<Reading> readings,
                       List<Sense> senses, boolean common, String source, String license) {}
    public record Hit(Word word, String matchType, String matchedForm, String matchedSenseId) {
        public Hit(Word word, String matchType, String matchedForm) { this(word, matchType, matchedForm, null); }
    }
    public record SearchResult(List<Hit> items, int page, boolean hasMore, String version, String query) {}
    public static final String SOURCE = "JMdict — EDRDG / James William Breen";
    public static final String LICENSE = "CC BY-SA 4.0";
    public static final String SOURCE_URL = "https://www.edrdg.org/pub/Nihongo/JMdict_e.gz";
}

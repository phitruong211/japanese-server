package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server side of the SoNhat deck format (N3/docs/deck-format-v1.md): note type definitions stored in
 * decks.template_config (version 5) and the per-card field values. The client validates the same rules; the
 * server never trusts it. Rules are shared through the fixtures in src/test/resources/deck-format.
 */
final class DeckFormat {
    private DeckFormat() {}

    static final int MAX_NOTE_TYPES = 16, MAX_FIELDS = 24, MAX_TEXT = 20_000, MAX_KANA = 10_000, MAX_EXAMPLES = 100, MAX_CARD_CHARS = 100_000;
    private static final Pattern KEY = Pattern.compile("^[a-z][A-Za-z0-9]{0,31}$");
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private static final Pattern FONT_ID = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9]{0,31}|custom:[0-9a-f-]{36})$");
    private static final Set<String> FIELD_TYPES = Set.of("text", "ruby", "kana", "examples");
    private static final Set<String> KINDS = Set.of("VOCABULARY", "KANJI", "GRAMMAR", "GENERAL");
    private static final Set<String> THEMES = Set.of("paper", "blue", "dark", "system");
    private static final Set<String> PSEUDO = Set.of("@kind", "@deckName");
    private static final Pattern MARKUP = Pattern.compile("</?(?:p|div|span|br|b|strong|i|em|u|s|small|mark|font|h[1-6]|ul|ol|li|blockquote|pre|code|ruby|rt|rp)(?:\\s|/?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUBY = Pattern.compile("([\\p{IsHan}々〆ヵヶ]+)\\[([^\\[\\]\\r\\n]+)\\]");

    record Field(String key, String type, boolean required) {}
    record NoteType(String id, String kind, List<Field> fields) {
        Field field(String key) { return fields.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null); }
    }
    /** Normalized field values plus the legacy columns derived from them. */
    record Validated(Map<String, Object> fields, String front, String back, String reading, String notes) {}

    private static final List<Field> STANDARD = List.of(
            new Field("front", "ruby", true), new Field("frontReading", "kana", false), new Field("back", "ruby", true),
            new Field("backReading", "kana", false), new Field("hanViet", "text", false), new Field("notes", "text", false),
            new Field("examples", "examples", false));
    private static final Map<String, NoteType> BUILTIN = new LinkedHashMap<>();
    static {
        BUILTIN.put("vocab", new NoteType("vocab", "VOCABULARY", STANDARD));
        BUILTIN.put("kanji", new NoteType("kanji", "KANJI", STANDARD));
        BUILTIN.put("grammar", new NoteType("grammar", "GRAMMAR", STANDARD));
        BUILTIN.put("general", new NoteType("general", "GENERAL", STANDARD));
    }
    static NoteType builtin(String id) { return BUILTIN.get(id); }

    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private static Map<?, ?> map(Object value, String path) {
        if (value instanceof Map<?, ?> m) return m;
        throw bad(path + " phải là một đối tượng");
    }

    /** Resolves the note types a card may use: the deck's own (template v5), else the built-in ones. */
    static final class Types {
        private final Map<String, NoteType> own = new LinkedHashMap<>();
        Types(Map<String, Object> template) {
            if (template != null && Integer.valueOf(5).equals(version(template))) parseNoteTypes(template.get("noteTypes")).forEach(t -> own.put(t.id(), t));
        }
        NoteType resolve(String id) {
            if (id == null || id.isBlank()) return own.isEmpty() ? BUILTIN.get("general") : own.values().iterator().next();
            NoteType type = own.containsKey(id) ? own.get(id) : BUILTIN.get(id);
            if (type == null) throw bad("Loại thẻ \"" + id + "\" không có trong bộ thẻ");
            return type;
        }
    }

    static Integer version(Map<String, Object> template) {
        return template.get("version") instanceof Number n ? n.intValue() : null;
    }

    /** Validates the `noteTypes` of a v5 template and returns them. Throws 400 on the first problem. */
    static List<NoteType> parseNoteTypes(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > MAX_NOTE_TYPES) throw bad("noteTypes cần 1–" + MAX_NOTE_TYPES + " loại thẻ");
        List<NoteType> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String path = "noteTypes[" + i + "]";
            Map<?, ?> type = map(list.get(i), path);
            if (!(type.get("id") instanceof String id) || !ID.matcher(id).matches()) throw bad(path + ".id không hợp lệ");
            if (!ids.add(id)) throw bad("id loại thẻ \"" + id + "\" bị trùng");
            if (!(type.get("name") instanceof String name) || name.isBlank() || name.trim().length() > 60) throw bad(path + ".name cần 1–60 ký tự");
            String kind = type.get("kind") == null ? "GENERAL" : String.valueOf(type.get("kind"));
            if (!KINDS.contains(kind)) throw bad(path + ".kind không hợp lệ");
            if (!(type.get("fields") instanceof List<?> rawFields) || rawFields.size() < 2 || rawFields.size() > MAX_FIELDS) throw bad(path + ".fields cần 2–" + MAX_FIELDS + " trường");
            List<Field> fields = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (int f = 0; f < rawFields.size(); f++) {
                String at = path + ".fields[" + f + "]";
                Map<?, ?> field = map(rawFields.get(f), at);
                if (!(field.get("key") instanceof String key) || !KEY.matcher(key).matches()) throw bad(at + ".key không hợp lệ");
                if (!keys.add(key)) throw bad(at + ".key bị trùng");
                if (!(field.get("type") instanceof String ft) || !FIELD_TYPES.contains(ft)) throw bad(at + ".type không hợp lệ");
                if (!(field.get("label") instanceof String label) || label.isBlank() || label.trim().length() > 60) throw bad(at + ".label cần 1–60 ký tự");
                if (field.get("required") != null && !(field.get("required") instanceof Boolean)) throw bad(at + ".required phải là true/false");
                if (field.get("style") != null) validateStyle(field.get("style"), at + ".style");
                fields.add(new Field(key, ft, Boolean.TRUE.equals(field.get("required"))));
            }
            for (int f = 0; f < rawFields.size(); f++) {
                Object target = ((Map<?, ?>) rawFields.get(f)).get("for");
                if (target != null && fields.stream().noneMatch(x -> x.key().equals(target) && x.type().equals("ruby"))) throw bad(path + ".fields[" + f + "].for phải trỏ tới một trường ruby");
            }
            for (String required : List.of("front", "back"))
                if (fields.stream().filter(x -> x.key().equals(required)).count() != 1) throw bad(path + ".fields cần đúng một trường \"" + required + "\"");
            for (String side : List.of("front", "back")) {
                Map<?, ?> face = map(type.get(side), path + "." + side);
                if (!(face.get("show") instanceof List<?> show) || show.isEmpty() || show.size() > MAX_FIELDS + PSEUDO.size()) throw bad(path + "." + side + ".show cần ít nhất một trường");
                for (Object key : show) if (!(key instanceof String s) || !(keys.contains(s) || PSEUDO.contains(s))) throw bad(path + "." + side + ".show chứa trường không tồn tại");
                if (face.get("showFront") != null && !(face.get("showFront") instanceof Boolean)) throw bad(path + "." + side + ".showFront phải là true/false");
                if (face.get("style") != null) validateStyle(face.get("style"), path + "." + side + ".style");
            }
            if (type.get("style") != null) validateStyle(type.get("style"), path + ".style");
            out.add(new NoteType(id, kind, List.copyOf(fields)));
        }
        return out;
    }

    static void validateStyle(Object raw, String path) {
        Map<?, ?> style = map(raw, path);
        for (Object key : style.keySet()) if (!Set.of("theme", "colors", "font", "align", "autoFit").contains(key)) throw bad(path + "." + key + " không được hỗ trợ");
        if (style.get("theme") != null && !THEMES.contains(String.valueOf(style.get("theme")))) throw bad(path + ".theme không được hỗ trợ");
        if (style.get("align") != null && !Set.of("left", "center").contains(String.valueOf(style.get("align")))) throw bad(path + ".align không được hỗ trợ");
        if (style.get("autoFit") != null && !(style.get("autoFit") instanceof Boolean)) throw bad(path + ".autoFit phải là true/false");
        if (style.get("colors") != null) {
            Map<?, ?> colors = map(style.get("colors"), path + ".colors");
            for (var entry : colors.entrySet()) {
                if (!Set.of("bg", "fg", "accent").contains(entry.getKey())) throw bad(path + ".colors." + entry.getKey() + " không được hỗ trợ");
                if (!(entry.getValue() instanceof String c) || !COLOR.matcher(c).matches()) throw bad(path + ".colors." + entry.getKey() + " phải dạng #rgb, #rrggbb hoặc #rrggbbaa");
            }
        }
        if (style.get("font") != null) {
            Map<?, ?> font = map(style.get("font"), path + ".font");
            for (var entry : font.entrySet()) {
                Object value = entry.getValue();
                switch (String.valueOf(entry.getKey())) {
                    case "family" -> { if (!(value instanceof String f) || !FONT_ID.matcher(f).matches()) throw bad(path + ".font.family không hợp lệ"); }
                    case "size" -> { if (!(value instanceof Integer n) || n < 12 || n > 72) throw bad(path + ".font.size phải là số nguyên 12–72"); }
                    case "bold", "italic" -> { if (!(value instanceof Boolean)) throw bad(path + ".font." + entry.getKey() + " phải là true/false"); }
                    default -> throw bad(path + ".font." + entry.getKey() + " không được hỗ trợ");
                }
            }
        }
    }

    /** Validates card field values against a note type; returns normalized values and the legacy columns. */
    static Validated validateFields(NoteType type, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        int total = 0;
        for (var entry : raw.entrySet()) {
            Field field = type.field(entry.getKey());
            if (field == null) throw bad("Trường \"" + entry.getKey() + "\" không thuộc loại thẻ " + type.id());
            Object value = entry.getValue();
            if (value == null) continue;
            if (field.type().equals("examples")) {
                List<Map<String, Object>> examples = examples(value, field.key());
                if (!examples.isEmpty()) out.put(field.key(), examples);
                total += examples.toString().length();
                continue;
            }
            if (!(value instanceof String text)) throw bad("Trường \"" + field.key() + "\" phải là chuỗi");
            int limit = field.type().equals("kana") ? MAX_KANA : MAX_TEXT;
            if (text.length() > limit) throw bad("Trường \"" + field.key() + "\" vượt " + limit + " ký tự");
            total += text.length();
            String clean = Normalizer.normalize(text.replaceAll("\\r\\n?", "\n").trim(), Normalizer.Form.NFC);
            if (!clean.isEmpty()) out.put(field.key(), clean);
        }
        if (total > MAX_CARD_CHARS) throw bad("Tổng dữ liệu của thẻ vượt giới hạn " + MAX_CARD_CHARS + " ký tự");
        for (Field field : type.fields()) if (field.required() && !(out.get(field.key()) instanceof String s && !s.isBlank())) throw bad("Thiếu trường bắt buộc \"" + field.key() + "\"");
        String front = (String) out.get("front"), back = (String) out.get("back");
        if (front == null || back == null) throw bad("Thiếu trường bắt buộc \"front\" hoặc \"back\"");
        String frontReading = kana(out.get("frontReading"));
        String reading = frontReading.isEmpty() ? Optional.ofNullable(readingOf(front)).orElse("") : frontReading;
        return new Validated(out, stripFurigana(front), stripFurigana(back), reading, (String) out.getOrDefault("notes", ""));
    }

    private static List<Map<String, Object>> examples(Object value, String key) {
        if (!(value instanceof List<?> list) || list.size() > MAX_EXAMPLES) throw bad("Trường \"" + key + "\" phải là danh sách tối đa " + MAX_EXAMPLES + " ví dụ");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m) || !(m.get("japanese") instanceof String jp) || jp.isBlank() || !(m.get("meaning") instanceof String meaning) || meaning.isBlank()
                    || (m.get("reading") != null && !(m.get("reading") instanceof String)))
                throw bad("Ví dụ cần japanese và meaning; reading là chuỗi tùy chọn");
            out.add(new LinkedHashMap<>(Map.of("japanese", jp.trim(), "reading", m.get("reading") instanceof String r ? r.trim() : "", "meaning", meaning.trim())));
        }
        return out;
    }

    private static String kana(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return "";
        String ruby = readingOf(s);
        return ruby != null ? ruby : s.trim();
    }

    /** Text without furigana markup; HTML and malformed markup are returned untouched (same rule as the client). */
    static String stripFurigana(String text) {
        if (text == null || MARKUP.matcher(text).find() || (text.indexOf('[') < 0 && text.indexOf(']') < 0)) return text;
        Matcher matcher = RUBY.matcher(text);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        boolean any = false;
        while (matcher.find()) { out.append(text, cursor, matcher.start()).append(matcher.group(1)); cursor = matcher.end(); any = true; }
        out.append(text.substring(cursor));
        String clean = out.toString();
        return any && clean.indexOf('[') < 0 && clean.indexOf(']') < 0 ? clean : text;
    }

    /**
     * Copy of a template or style with every `font.family` that names an uploaded font (`custom:<id>`) removed. Uploaded
     * fonts belong to their owner and are never shared, so a copy must not point at them.
     */
    static Object stripCustomFonts(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.equals("font") && entry.getValue() instanceof Map<?, ?> font) {
                    Map<String, Object> kept = new LinkedHashMap<>();
                    font.forEach((k, v) -> { if (!(String.valueOf(k).equals("family") && v instanceof String s && s.startsWith("custom:"))) kept.put(String.valueOf(k), v); });
                    if (!kept.isEmpty()) out.put(key, kept);
                } else out.put(key, stripCustomFonts(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) return new ArrayList<>(list.stream().map(DeckFormat::stripCustomFonts).toList());
        return value;
    }

    /** Kana reading spelled by explicit markup, or null when there is none. */
    static String readingOf(String text) {
        if (text == null || MARKUP.matcher(text).find() || (text.indexOf('[') < 0 && text.indexOf(']') < 0)) return null;
        Matcher matcher = RUBY.matcher(text);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        boolean any = false;
        while (matcher.find()) { out.append(text, cursor, matcher.start()).append(matcher.group(2).trim()); cursor = matcher.end(); any = true; }
        out.append(text.substring(cursor));
        String result = out.toString();
        return any && result.indexOf('[') < 0 && result.indexOf(']') < 0 ? result : null;
    }
}

package com.japaneselearning.japanese_learning_api.dictionary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

public final class DictionaryNormalizer {
    private static final java.util.regex.Pattern SPACE=java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern MARKS=java.util.regex.Pattern.compile("\\p{M}");
    private static final java.util.regex.Pattern SEPARATORS=java.util.regex.Pattern.compile("[^\\p{L}\\p{N}]+");
    private DictionaryNormalizer() {}
    public static String normalize(String value) {
        String s = SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(c -> out.appendCodePoint(c >= 0x30a1 && c <= 0x30f6 ? c - 0x60 : c));
        return out.toString();
    }
    public static List<String> tokens(String value) {
        String s = MARKS.matcher(Normalizer.normalize(normalize(value), Normalizer.Form.NFD)).replaceAll("").replace('đ','d');
        return Arrays.stream(SEPARATORS.split(s))
            .filter(t -> !t.isBlank() && t.length() <= 100).distinct().limit(100).toList();
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}

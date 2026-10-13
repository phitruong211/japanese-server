package com.japaneselearning.japanese_learning_api.dictionary;

import java.util.*;

/** Small independently authored grammar table. Candidates are never accepted without dictionary POS checks. */
public final class JapaneseDeinflector {
    public record Candidate(String form, String pos, String reason) {}
    private record Rule(String suffix, String ending, String pos, String reason) {}
    private static final List<Rule> RULES = rules();
    private static List<Rule> rules() {
        List<Rule> out = new ArrayList<>();
        for (String suffix : List.of("ませんでした", "ました", "ません", "ます")) {
            out.add(new Rule(suffix, "る", "ichidan", "thể lịch sự"));
            String[] rows = {"い:う", "き:く", "ぎ:ぐ", "し:す", "ち:つ", "に:ぬ", "び:ぶ", "み:む", "り:る"};
            for (String row : rows) {
                String[] parts = row.split(":");
                out.add(new Rule(parts[0] + suffix, parts[1], "godan-" + parts[1], "thể lịch sự"));
            }
            out.add(new Rule("し" + suffix, "する", "suru", "する · thể lịch sự"));
            out.add(new Rule("き" + suffix, "くる", "kuru", "来る · thể lịch sự"));
            out.add(new Rule("来" + suffix, "来る", "kuru", "来る · thể lịch sự"));
        }
        for (String suffix : List.of("ない", "なかった")) {
            out.add(new Rule(suffix, "る", "ichidan", "thể phủ định"));
            String[] rows = {"わ:う", "か:く", "が:ぐ", "さ:す", "た:つ", "な:ぬ", "ば:ぶ", "ま:む", "ら:る"};
            for (String row : rows) {
                String[] parts = row.split(":");
                out.add(new Rule(parts[0] + suffix, parts[1], "godan-" + parts[1], "thể phủ định"));
            }
            out.add(new Rule("し" + suffix, "する", "suru", "する · thể phủ định"));
            out.add(new Rule("こ" + suffix, "くる", "kuru", "来る · thể phủ định"));
            out.add(new Rule("来" + suffix, "来る", "kuru", "来る · thể phủ định"));
        }
        for (String suffix : List.of("て", "た")) {
            out.add(new Rule(suffix, "る", "ichidan", "thể て / quá khứ"));
            out.add(new Rule("い" + suffix, "く", "godan-く-regular", "thể て / quá khứ"));
            out.add(new Rule("し" + suffix, "す", "godan-す", "thể て / quá khứ"));
            out.add(new Rule("し" + suffix, "する", "suru", "する · thể て / quá khứ"));
            for (String ending : List.of("う", "つ", "る")) out.add(new Rule("っ" + suffix, ending, "godan-" + ending, "thể て / quá khứ"));
            out.add(new Rule("っ" + suffix, "く", "iku", "行く · thể て / quá khứ"));
            out.add(new Rule("き" + suffix, "くる", "kuru", "来る · thể て / quá khứ"));
            out.add(new Rule("来" + suffix, "来る", "kuru", "来る · thể て / quá khứ"));
        }
        for (String suffix : List.of("で", "だ")) {
            out.add(new Rule("い" + suffix, "ぐ", "godan-ぐ", "thể て / quá khứ"));
            for (String ending : List.of("む", "ぶ", "ぬ")) out.add(new Rule("ん" + suffix, ending, "godan-" + ending, "thể て / quá khứ"));
        }
        for (String suffix : List.of("かった", "くない", "くなかった", "くて")) out.add(new Rule(suffix, "い", "adjective-i", "tính từ い"));
        return List.copyOf(out);
    }
    public List<Candidate> candidates(String query) {
        // One inflection layer for MVP; no unrestricted suffix recursion.
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            if (!query.endsWith(rule.suffix()) || query.length() < rule.suffix().length()) continue;
            String form = query.substring(0, query.length() - rule.suffix().length()) + rule.ending();
            if (form.equals(query) || form.length() < 2) continue;
            candidates.putIfAbsent(form + ":" + rule.pos(), new Candidate(form, rule.pos(), rule.reason()));
            if (rule.pos().equals("suru") && form.endsWith("する") && form.length() > 2) {
                String noun = form.substring(0, form.length() - 2);
                candidates.putIfAbsent(noun + ":suru", new Candidate(noun, "suru", rule.reason()));
            }
            if (candidates.size() == 64) break;
        }
        return List.copyOf(candidates.values());
    }
    public boolean matches(String expected, List<String> partsOfSpeech) {
        for (String raw : partsOfSpeech) {
            String pos = raw.toLowerCase(Locale.ROOT);
            if (expected.equals("ichidan") && pos.contains("ichidan")) return true;
            if (expected.equals("suru") && pos.contains("suru")) return true;
            if (expected.equals("kuru") && pos.contains("kuru")) return true;
            if (expected.equals("adjective-i") && (pos.contains("adjective (keiyoushi)") || pos.contains("i-adjective"))) return true;
            if (expected.equals("iku") && pos.contains("godan") && pos.contains("iku")) return true;
            if (expected.startsWith("godan-") && pos.contains("godan")) {
                if (expected.equals("godan-く") && pos.contains("iku")) return true;
                String romaji = switch (expected.substring(6).replace("-regular", "")) {
                    case "う" -> "u"; case "く" -> "ku"; case "ぐ" -> "gu"; case "す" -> "su";
                    case "つ" -> "tsu"; case "ぬ" -> "nu"; case "ぶ" -> "bu"; case "む" -> "mu"; case "る" -> "ru"; default -> "INVALID";
                };
                // 行く has a special て/た form and should not pass the regular ku past/te rule.
                if (pos.contains("'" + romaji + "'") || pos.contains("\"" + romaji + "\"")) return true;
            }
        }
        return false;
    }
}

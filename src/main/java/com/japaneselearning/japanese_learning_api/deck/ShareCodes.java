package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Short share codes ("K7QM-3XPD"): 8 characters from an alphabet without look-alikes (no 0 O 1 I L), about 40 bits.
 * That is guessable by brute force, so lookups are rate limited (see ShareCodeController) and codes can expire.
 */
final class ShareCodes {
    private ShareCodes() {}

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;
    private static final Pattern VALID = Pattern.compile("^[" + ALPHABET + "]{" + LENGTH + "}$");
    private static final Set<Integer> EXPIRY_DAYS = Set.of(1, 7, 30);
    private static final SecureRandom RANDOM = new SecureRandom();

    static String generate(Predicate<String> taken) {
        for (int attempt = 0; attempt < 25; attempt++) {
            StringBuilder code = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (!taken.test(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Không tạo được mã chia sẻ duy nhất");
    }

    /** Accepts what people type: any case, spaces or hyphens. Returns null when it cannot be a code. */
    static String normalize(String raw) {
        if (raw == null) return null;
        String code = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return VALID.matcher(code).matches() ? code : null;
    }

    static String display(String code) { return code == null ? null : code.substring(0, 4) + "-" + code.substring(4); }

    /** null means the link does not expire. */
    static Instant expiry(Integer days) {
        if (days == null) return null;
        if (!EXPIRY_DAYS.contains(days)) throw new ApiException(HttpStatus.BAD_REQUEST, "Thời hạn liên kết chỉ có thể là 1, 7 hoặc 30 ngày");
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    static boolean expired(Instant expiresAt) { return expiresAt != null && !expiresAt.isAfter(Instant.now()); }
}

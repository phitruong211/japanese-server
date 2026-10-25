package com.japaneselearning.japanese_learning_api.font;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Checks that an upload really is a .woff2 or .ttf font before it is stored, by reading its binary structure rather
 * than trusting the file name or content type. Browsers parse fonts with a hardened sanitizer, so this is a gate against
 * junk and abuse (wrong files, truncated files, absurd sizes), not a full font parser: WOFF2 payloads are Brotli
 * compressed and are not unpacked here.
 */
public final class FontFileValidator {
    private FontFileValidator() {}

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MIN_BYTES = 256;
    private static final int WOFF2 = 0x774F4632, TRUETYPE = 0x00010000, TRUE = 0x74727565, OTTO = 0x4F54544F;
    private static final int MAX_TABLES = 64;
    private static final long MAX_UNPACKED = 32L * 1024 * 1024;
    private static final Set<String> REQUIRED = Set.of("cmap", "head", "hhea", "hmtx", "maxp");

    public record Parsed(String format, int size, String sha256) {}

    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    public static Parsed validate(String filename, byte[] data) {
        if (data.length < MIN_BYTES) throw bad("Tệp quá nhỏ để là một font.");
        if (data.length > MAX_BYTES) throw bad("Font tối đa " + MAX_BYTES / (1024 * 1024) + " MB.");
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String extension = name.endsWith(".woff2") ? "woff2" : name.endsWith(".ttf") ? "ttf" : null;
        if (extension == null) throw bad("Chỉ nhận font .woff2 hoặc .ttf.");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int magic = buffer.getInt(0);
        String format;
        if (magic == WOFF2) { checkWoff2(buffer); format = "woff2"; }
        else if (magic == TRUETYPE || magic == TRUE) { checkSfnt(buffer); format = "ttf"; }
        else throw bad("Tệp không phải font .woff2 hoặc .ttf hợp lệ.");
        if (!format.equals(extension)) throw bad("Phần mở rộng ." + extension + " không khớp nội dung tệp (" + format + ").");
        return new Parsed(format, data.length, sha256(data));
    }

    private static void checkWoff2(ByteBuffer b) {
        if (b.limit() < 48) throw bad("Font .woff2 bị cắt cụt.");
        int flavor = b.getInt(4);
        if (flavor != TRUETYPE && flavor != OTTO && flavor != TRUE) throw bad("Font .woff2 có kiểu dữ liệu không được hỗ trợ.");
        if ((b.getInt(8) & 0xFFFFFFFFL) != b.limit()) throw bad("Độ dài khai báo của font .woff2 không khớp tệp.");
        int tables = b.getShort(12) & 0xFFFF;
        if (tables < 1 || tables > 255 || (b.getShort(14) & 0xFFFF) != 0) throw bad("Phần đầu của font .woff2 không hợp lệ.");
        long unpacked = b.getInt(16) & 0xFFFFFFFFL, packed = b.getInt(20) & 0xFFFFFFFFL;
        if (unpacked < 12 || unpacked > MAX_UNPACKED) throw bad("Kích thước giải nén của font .woff2 không hợp lệ.");
        if (packed == 0 || packed > b.limit() - 48L) throw bad("Dữ liệu nén của font .woff2 không hợp lệ.");
    }

    private static void checkSfnt(ByteBuffer b) {
        int tables = b.getShort(4) & 0xFFFF;
        long directoryEnd = 12L + 16L * tables;
        if (tables < 1 || tables > MAX_TABLES || directoryEnd > b.limit()) throw bad("Bảng bảng-mục của font .ttf không hợp lệ.");
        Set<String> tags = new HashSet<>();
        long headOffset = -1, headLength = 0;
        for (int i = 0; i < tables; i++) {
            int at = 12 + 16 * i;
            StringBuilder tag = new StringBuilder(4);
            for (int c = 0; c < 4; c++) tag.append((char) (b.get(at + c) & 0xFF));
            long offset = b.getInt(at + 8) & 0xFFFFFFFFL, length = b.getInt(at + 12) & 0xFFFFFFFFL;
            if (!tags.add(tag.toString())) throw bad("Font .ttf có bảng lặp: " + tag);
            if (offset < directoryEnd || length == 0 || offset + length > b.limit()) throw bad("Bảng \"" + tag + "\" của font .ttf nằm ngoài tệp.");
            if (tag.toString().equals("head")) { headOffset = offset; headLength = length; }
        }
        if (!tags.containsAll(REQUIRED) || !((tags.contains("glyf") && tags.contains("loca")) || tags.contains("CFF ") || tags.contains("CFF2")))
            throw bad("Font .ttf thiếu các bảng bắt buộc (cmap, head, hhea, hmtx, maxp, glyph).");
        if (headLength < 54 || b.getInt((int) headOffset + 12) != 0x5F0F3CF5) throw bad("Bảng \"head\" của font .ttf không hợp lệ.");
    }

    private static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

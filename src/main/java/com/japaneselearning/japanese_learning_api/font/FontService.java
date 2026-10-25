package com.japaneselearning.japanese_learning_api.font;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Per-user font library. A user keeps at most MAX_FONTS fonts and MAX_TOTAL_BYTES in total; fonts are never shared. */
@Service
public class FontService {
    public static final int MAX_FONTS = 10;
    public static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

    public record FontInfo(UUID id, String name, String format, int sizeBytes, Instant createdAt) {}
    public record Limits(int maxFonts, int maxFileBytes, long maxTotalBytes, long usedBytes) {}
    public record FontList(List<FontInfo> fonts, Limits limits) {}
    public record Uploaded(FontInfo font, boolean created) {}
    public record FontFileData(String format, byte[] data) {}

    private final UserFontRepository fonts;
    private final UserFontFileRepository files;
    @PersistenceContext private EntityManager em;

    public FontService(UserFontRepository fonts, UserFontFileRepository files) { this.fonts = fonts; this.files = files; }

    private static FontInfo info(UserFont font) { return new FontInfo(font.getId(), font.getName(), font.getFormat(), font.getSizeBytes(), font.getCreatedAt()); }

    @Transactional(readOnly = true)
    public FontList list(UUID userId) {
        return new FontList(fonts.findAllByOwnerIdOrderByCreatedAtDesc(userId).stream().map(FontService::info).toList(), limits(userId));
    }

    private Limits limits(UUID userId) { return new Limits(MAX_FONTS, FontFileValidator.MAX_BYTES, MAX_TOTAL_BYTES, fonts.totalBytes(userId)); }

    @Transactional
    public Uploaded upload(UUID userId, String requestedName, String filename, byte[] data) {
        // Serialize one owner's uploads so concurrent requests cannot both pass the quota check.
        if (em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) throw ApiException.notFound("Không tìm thấy người dùng");
        var parsed = FontFileValidator.validate(filename, data);
        var existing = fonts.findByOwnerIdAndSha256(userId, parsed.sha256());
        if (existing.isPresent()) return new Uploaded(info(existing.get()), false);
        if (fonts.countByOwnerId(userId) >= MAX_FONTS) throw ApiException.conflict("Bạn đã dùng hết " + MAX_FONTS + " font. Hãy xóa bớt font không dùng.");
        if (fonts.totalBytes(userId) + parsed.size() > MAX_TOTAL_BYTES) throw ApiException.conflict("Tổng dung lượng font vượt " + MAX_TOTAL_BYTES / (1024 * 1024) + " MB. Hãy xóa bớt font không dùng.");
        var font = new UserFont();
        font.setId(UUID.randomUUID());
        font.setOwnerId(userId);
        font.setName(displayName(requestedName, filename));
        font.setFormat(parsed.format());
        font.setSizeBytes(parsed.size());
        font.setSha256(parsed.sha256());
        font.setCreatedAt(Instant.now());
        fonts.save(font);
        var file = new UserFontFile();
        file.setFontId(font.getId());
        file.setData(data);
        files.save(file);
        return new Uploaded(info(font), true);
    }

    @Transactional(readOnly = true)
    public FontFileData file(UUID userId, UUID id) {
        var font = fonts.findByIdAndOwnerId(id, userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy font"));
        var file = files.findById(font.getId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy font"));
        return new FontFileData(font.getFormat(), file.getData());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        var font = fonts.findByIdAndOwnerId(id, userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy font"));
        files.deleteById(font.getId());
        fonts.delete(font);
    }

    static String displayName(String requested, String filename) {
        String base = requested != null && !requested.isBlank() ? requested : (filename == null ? "" : filename.replaceAll("(?i)\\.(woff2|ttf)$", ""));
        String clean = base.replaceAll("\\p{Cc}+", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) clean = "Font của tôi";
        return clean.length() > 60 ? clean.substring(0, 60).trim() : clean;
    }
}

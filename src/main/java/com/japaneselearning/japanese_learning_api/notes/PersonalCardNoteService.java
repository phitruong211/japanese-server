package com.japaneselearning.japanese_learning_api.notes;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PersonalCardNoteService {
    public record Note(String key, String text, long version, Instant updatedAt) {}
    public record Update(@NotNull @Size(max = 5000) String text, @Min(0) long version) {}
    private final PersonalCardNoteRepository notes;
    @PersistenceContext private EntityManager em;
    public PersonalCardNoteService(PersonalCardNoteRepository notes) { this.notes = notes; }

    @Transactional(readOnly = true)
    public Note get(UUID owner, String key) {
        validateKey(key);
        return notes.findByOwnerIdAndNoteKey(owner, key).map(this::view).orElse(new Note(key, "", 0, null));
    }

    @Transactional
    public Note put(UUID owner, String key, Update request) {
        validateKey(key);
        if (request.text() == null || request.text().length() > 5000 || request.version() < 0)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Ghi chú tối đa 5.000 ký tự");
        // Also serialize first creation; there is no note row to lock yet.
        if (em.find(User.class, owner, LockModeType.PESSIMISTIC_WRITE) == null) throw ApiException.unauthorized("Tài khoản không tồn tại");
        PersonalCardNote row = notes.findByOwnerIdAndNoteKey(owner, key).orElse(null);
        long version = row == null ? 0 : row.getRevision();
        String text = request.text().strip();
        if (version != request.version()) {
            // A retry after a lost acknowledgement is harmless; a different edit is a conflict.
            if (row != null && row.getContent().equals(text)) return view(row);
            throw ApiException.conflict("Ghi chú đã thay đổi trên thiết bị khác. Chọn bản muốn giữ.");
        }
        if (row == null) {
            row = new PersonalCardNote(); row.setId(UUID.randomUUID()); row.setOwnerId(owner); row.setNoteKey(key);
        }
        row.setContent(text); row.setRevision(version + 1); row.setUpdatedAt(Instant.now());
        return view(notes.save(row));
    }

    public static String personalKey(UUID cardId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("PERSONAL:" + cardId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public Note view(PersonalCardNote row) { return new Note(row.getNoteKey(), row.getContent(), row.getRevision(), row.getUpdatedAt()); }
    private void validateKey(String key) {
        if (key == null || !key.matches("[0-9a-f]{64}"))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Mã ghi chú không hợp lệ");
    }
}

package com.japaneselearning.japanese_learning_api.notes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "personal_card_notes", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "note_key"}))
@Getter @Setter
public class PersonalCardNote {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "note_key", nullable = false, length = 64) private String noteKey;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false) private long revision;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}

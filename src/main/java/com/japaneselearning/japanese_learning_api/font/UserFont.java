package com.japaneselearning.japanese_learning_api.font;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "user_fonts", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "sha256"}))
@Getter @Setter
public class UserFont {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false, length = 10) private String format;
    @Column(name = "size_bytes", nullable = false) private int sizeBytes;
    @Column(nullable = false, length = 64, columnDefinition = "char(64)") private String sha256;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

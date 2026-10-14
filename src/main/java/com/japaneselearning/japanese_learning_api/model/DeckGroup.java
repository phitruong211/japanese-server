package com.japaneselearning.japanese_learning_api.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "deck_groups", uniqueConstraints = @UniqueConstraint(name = "ux_deck_group_import_key", columnNames = {"owner_id", "import_key"})) @Getter @Setter @NoArgsConstructor
public class DeckGroup {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private User owner;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false) private int position;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "share_token", length = 36, unique = true) private String shareToken;
    @Column(name = "share_personal_notes", nullable = false) private boolean sharePersonalNotes;
    @Column(name = "import_key", length = 128) private String importKey;
    @Version private long version;
}

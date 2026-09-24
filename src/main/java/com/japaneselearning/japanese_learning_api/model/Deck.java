package com.japaneselearning.japanese_learning_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "decks")
@Getter @Setter @NoArgsConstructor
public class Deck {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private User owner;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private DomainTypes.SourceType sourceType = DomainTypes.SourceType.MANUAL;
    @Column(name = "source_name") private String sourceName;
    @Column(name = "import_format", length = 20) private String importFormat;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.Visibility visibility = DomainTypes.Visibility.PRIVATE;
    @Column(name = "card_count", nullable = false) private int cardCount;
    @Column(nullable = false) private int position;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "template_config", columnDefinition = "json", nullable = false)
    private Map<String, Object> templateConfig = new HashMap<>();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;
}

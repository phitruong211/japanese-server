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
@Table(name = "cards")
@Getter @Setter @NoArgsConstructor
public class Card {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "deck_id") private Deck deck;
    @Column(nullable = false, columnDefinition = "text") private String front;
    @Column(nullable = false, columnDefinition = "text") private String back;
    @Column(columnDefinition = "text") private String reading;
    @Column(columnDefinition = "text") private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.CardKind kind = DomainTypes.CardKind.GENERAL;
    @Column(nullable = false) private int position;
    @Column(name = "external_id") private String externalId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "extra_data", columnDefinition = "json", nullable = false)
    private Map<String, Object> extraData = new HashMap<>();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(length = 30) private String source;
    @Column(name = "source_ref", length = 1000) private String sourceRef;
    @Column(name = "source_sheet") private String sourceSheet;
    @Version private long version;
}

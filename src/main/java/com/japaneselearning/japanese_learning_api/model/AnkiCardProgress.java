package com.japaneselearning.japanese_learning_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anki_card_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "card_id"}))
@Getter @Setter @NoArgsConstructor
public class AnkiCardProgress {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "card_id") private Card card;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.AnkiState state = DomainTypes.AnkiState.NEW;
    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2) private BigDecimal easeFactor = new BigDecimal("2.50");
    @Column(name = "interval_minutes") private Integer intervalMinutes;
    @Column(name = "interval_days") private Integer intervalDays;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(nullable = false) private int repetitions;
    @Column(nullable = false) private int lapses;
    @Column(name = "last_reviewed_at") private Instant lastReviewedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
}

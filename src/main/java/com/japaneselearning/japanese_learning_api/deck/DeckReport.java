package com.japaneselearning.japanese_learning_api.deck;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "deck_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"deck_id", "reporter_id"}))
@Getter @Setter
public class DeckReport {
    public static final String OPEN = "OPEN", DISMISSED = "DISMISSED", ACTIONED = "ACTIONED";
    @Id private UUID id;
    @Column(name = "deck_id", nullable = false) private UUID deckId;
    @Column(name = "reporter_id", nullable = false) private UUID reporterId;
    @Column(nullable = false, length = 16) private String reason;
    @Column(length = 500) private String details;
    @Column(nullable = false, length = 16) private String status = OPEN;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "handled_at") private Instant handledAt;
    @Column(name = "handled_by") private UUID handledBy;
}

package com.japaneselearning.japanese_learning_api.learning;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** Built-in content has string IDs and no row in cards; retain its events separately. */
@Entity @Table(name="builtin_review_logs", uniqueConstraints=@UniqueConstraint(columnNames={"user_id", "request_id"}))
@Getter @Setter @NoArgsConstructor
public class BuiltInReviewLog {
    @Id private UUID id;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Column(name="request_id", nullable=false) private UUID requestId;
    @Column(nullable=false, length=200) private String cardId;
    @Column(nullable=false, length=20) private String deckType;
    @Column(nullable=false, length=10) private String rating;
    @Column(nullable=false) private Instant reviewedAt;
    private Double predictedRecall;
    private Double elapsedDays;
    private boolean timeValid;
    @Column(length=80) private String schedulerVersion;
    @Column(nullable=false, columnDefinition="longtext") private String telemetry;
    @Column(nullable=false, columnDefinition="longtext") private String resultCard;
}

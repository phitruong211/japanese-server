package com.japaneselearning.japanese_learning_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anki_review_logs")
@Getter @Setter @NoArgsConstructor
public class AnkiReviewLog {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "card_id") private Card card;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DomainTypes.AnkiRating rating;
    @Enumerated(EnumType.STRING) @Column(name = "previous_state", nullable = false) private DomainTypes.AnkiState previousState;
    @Enumerated(EnumType.STRING) @Column(name = "new_state", nullable = false) private DomainTypes.AnkiState newState;
    @Column(name = "previous_interval_days") private Integer previousIntervalDays;
    @Column(name = "new_interval_days") private Integer newIntervalDays;
    @Column(name = "response_time_ms") private Integer responseTimeMs;
    @Column(name = "recall_time_ms") private Integer recallTimeMs;
    @Column(name = "decision_time_ms") private Integer decisionTimeMs;
    @Column(name = "is_time_valid") private Boolean timeValid;
    @Column(name = "shown_at") private Instant shownAt;
    @Column(name = "revealed_at") private Instant revealedAt;
    @Column(name = "rated_at") private Instant ratedAt;
    @Column(name = "elapsed_days") private Double elapsedDays;
    @Column(name = "predicted_recall") private Double predictedRecall;
    @Column(name = "scheduler_version") private String schedulerVersion;
    @Column(name = "reviewed_at", nullable = false) private Instant reviewedAt;
    @Column(name = "client_review_id") private UUID clientReviewId;
    @Lob @Column(name = "result_progress", columnDefinition = "longtext") private String resultProgress;
}

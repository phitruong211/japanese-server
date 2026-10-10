package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.AnkiReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface AnkiReviewLogRepository extends JpaRepository<AnkiReviewLog, UUID> {
    Optional<AnkiReviewLog> findByUserIdAndClientReviewId(UUID userId, UUID clientReviewId);
}

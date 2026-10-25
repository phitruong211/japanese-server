package com.japaneselearning.japanese_learning_api.deck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeckReportRepository extends JpaRepository<DeckReport, UUID> {
    boolean existsByDeckIdAndReporterId(UUID deckId, UUID reporterId);
    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);
    long countByDeckIdAndStatus(UUID deckId, String status);
    List<DeckReport> findAllByDeckIdAndStatus(UUID deckId, String status);
    Page<DeckReport> findAllByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}

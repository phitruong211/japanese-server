package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnkiCardProgressRepository extends JpaRepository<AnkiCardProgress, UUID> {
    Optional<AnkiCardProgress> findByUserIdAndCardId(UUID userId, UUID cardId);
    List<AnkiCardProgress> findAllByUserIdAndCardDeckId(UUID userId, UUID deckId);

    @Query("""
        select p from AnkiCardProgress p
        join fetch p.card c join fetch c.deck d
        where p.user.id = :userId and p.dueAt <= :now
          and c.deletedAt is null and d.deletedAt is null and d.owner.id = :userId
          and p.state <> com.japaneselearning.japanese_learning_api.model.DomainTypes$AnkiState.NEW
          and (:deckId is null or d.id = :deckId)
        order by p.dueAt, p.id
        """)
    List<AnkiCardProgress> findDue(@Param("userId") UUID userId, @Param("deckId") UUID deckId, @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}

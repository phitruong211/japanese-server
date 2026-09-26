package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Card> {
    List<Card> findAllByDeckIdAndDeletedAtIsNullOrderByPosition(UUID deckId);
    Optional<Card> findByIdAndDeckOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
    long countByDeckIdAndDeletedAtIsNull(UUID deckId);

    @Query("select count(c) from Card c where c.deck.id = :deckId and c.deletedAt is null and not exists (select p.id from AnkiCardProgress p where p.card.id = c.id and p.user.id = :userId and p.state <> com.japaneselearning.japanese_learning_api.model.DomainTypes$AnkiState.NEW)")
    long countNew(@Param("deckId") UUID deckId, @Param("userId") UUID userId);

    @Query("select count(p) from AnkiCardProgress p where p.card.deck.id = :deckId and p.card.deletedAt is null and p.user.id = :userId and p.state <> com.japaneselearning.japanese_learning_api.model.DomainTypes$AnkiState.NEW and p.dueAt <= :now")
    long countDue(@Param("deckId") UUID deckId, @Param("userId") UUID userId, @Param("now") java.time.Instant now);

    @Query("""
        select c from Card c join fetch c.deck d
        where d.owner.id = :userId and c.deletedAt is null and d.deletedAt is null
          and (:deckId is null or d.id = :deckId)
          and not exists (select p.id from AnkiCardProgress p where p.user.id = :userId and p.card.id = c.id and p.state <> com.japaneselearning.japanese_learning_api.model.DomainTypes$AnkiState.NEW)
        order by d.updatedAt desc, c.position, c.id
        """)
    List<Card> findNewForAnki(@Param("userId") UUID userId, @Param("deckId") UUID deckId, org.springframework.data.domain.Pageable pageable);
}

package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findAllByDeckIdAndDeletedAtIsNullOrderByPosition(UUID deckId);
    Optional<Card> findByIdAndDeckOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
    long countByDeckIdAndDeletedAtIsNull(UUID deckId);

    @Query("""
        select c from Card c join fetch c.deck d
        where d.owner.id = :userId and c.deletedAt is null and d.deletedAt is null
          and (:deckId is null or d.id = :deckId)
          and not exists (select p.id from AnkiCardProgress p where p.user.id = :userId and p.card.id = c.id)
        order by d.updatedAt desc, c.position
        """)
    List<Card> findNewForAnki(@Param("userId") UUID userId, @Param("deckId") UUID deckId);
}

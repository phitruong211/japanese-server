package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {
    List<Deck> findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(UUID ownerId);
    Optional<Deck> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}

package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {
    org.springframework.data.domain.Page<Deck> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId, org.springframework.data.domain.Pageable pageable);
    long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    Optional<Deck> findByOwnerIdAndImportKey(UUID ownerId, String importKey);
    List<Deck> findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(UUID ownerId);
    Optional<Deck> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}

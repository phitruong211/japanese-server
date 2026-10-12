package com.japaneselearning.japanese_learning_api.repository;
import com.japaneselearning.japanese_learning_api.model.DeckGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeckGroupRepository extends JpaRepository<DeckGroup, UUID> {
    List<DeckGroup> findAllByOwnerIdOrderByPositionAscIdAsc(UUID ownerId);
    Optional<DeckGroup> findByIdAndOwnerId(UUID id, UUID ownerId);
}

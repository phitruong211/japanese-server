package com.japaneselearning.japanese_learning_api.repository;
import com.japaneselearning.japanese_learning_api.model.DeckGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeckGroupRepository extends JpaRepository<DeckGroup, UUID> {
    Optional<DeckGroup> findByShareToken(String shareToken);
    Optional<DeckGroup> findByShareCode(String shareCode);
    boolean existsByShareCode(String shareCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from DeckGroup g where g.owner.id = :ownerId and g.importKey = :key")
    Optional<DeckGroup> findSharedCopyForUpdate(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
        @org.springframework.data.repository.query.Param("key") String key);
    List<DeckGroup> findAllByOwnerIdOrderByPositionAscIdAsc(UUID ownerId);
    Optional<DeckGroup> findByIdAndOwnerId(UUID id, UUID ownerId);
}

package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {
    List<Deck> findAllByOwnerIdAndGroupIdAndDeletedAtIsNullOrderByPositionAscIdAsc(UUID ownerId, UUID groupId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select d from Deck d where d.owner.id = :ownerId and d.groupId = :groupId and d.deletedAt is null order by d.position, d.id")
    List<Deck> findGroupForShareCopy(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
        @org.springframework.data.repository.query.Param("groupId") UUID groupId);
    Optional<Deck> findByShareTokenAndDeletedAtIsNull(String shareToken);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Deck d where d.owner.id = :ownerId and d.importKey = :key")
    Optional<Deck> findSharedCopyForUpdate(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
                                         @org.springframework.data.repository.query.Param("key") String key);
    org.springframework.data.domain.Page<Deck> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Deck> findAllByOwnerIdAndGroupIdAndDeletedAtIsNull(UUID ownerId, UUID groupId, org.springframework.data.domain.Pageable pageable);
    long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    Optional<Deck> findByOwnerIdAndImportKey(UUID ownerId, String importKey);
    List<Deck> findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(UUID ownerId);
    Optional<Deck> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}

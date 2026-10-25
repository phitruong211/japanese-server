package com.japaneselearning.japanese_learning_api.font;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFontRepository extends JpaRepository<UserFont, UUID> {
    List<UserFont> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    Optional<UserFont> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<UserFont> findByOwnerIdAndSha256(UUID ownerId, String sha256);
    long countByOwnerId(UUID ownerId);
    @Query("select coalesce(sum(f.sizeBytes), 0) from UserFont f where f.ownerId = :ownerId")
    long totalBytes(UUID ownerId);
}

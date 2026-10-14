package com.japaneselearning.japanese_learning_api.notes;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PersonalCardNoteRepository extends JpaRepository<PersonalCardNote, UUID> {
    Optional<PersonalCardNote> findByOwnerIdAndNoteKey(UUID ownerId, String noteKey);
    List<PersonalCardNote> findByOwnerIdAndNoteKeyIn(UUID ownerId, Collection<String> keys);
}

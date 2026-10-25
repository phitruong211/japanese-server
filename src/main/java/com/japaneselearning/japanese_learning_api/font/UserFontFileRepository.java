package com.japaneselearning.japanese_learning_api.font;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserFontFileRepository extends JpaRepository<UserFontFile, UUID> {}

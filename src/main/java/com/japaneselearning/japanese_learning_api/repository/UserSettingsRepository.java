package com.japaneselearning.japanese_learning_api.repository;

import com.japaneselearning.japanese_learning_api.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {}

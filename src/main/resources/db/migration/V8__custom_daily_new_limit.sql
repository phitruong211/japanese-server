ALTER TABLE user_settings
  ADD COLUMN srs_daily_new_limit INTEGER NOT NULL DEFAULT 20,
  ADD CONSTRAINT ck_settings_srs_daily_new_limit CHECK (srs_daily_new_limit >= 0);

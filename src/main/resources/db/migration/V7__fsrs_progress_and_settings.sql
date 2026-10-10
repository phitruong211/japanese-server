ALTER TABLE user_settings
  ADD COLUMN srs_again_minutes INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN srs_good_minutes INTEGER NOT NULL DEFAULT 10,
  ADD COLUMN srs_desired_retention NUMERIC(4,3) NOT NULL DEFAULT 0.900,
  ADD CONSTRAINT ck_settings_srs_again CHECK (srs_again_minutes BETWEEN 1 AND 30),
  ADD CONSTRAINT ck_settings_srs_good CHECK (srs_good_minutes BETWEEN 2 AND 720),
  ADD CONSTRAINT ck_settings_srs_order CHECK (srs_good_minutes > srs_again_minutes),
  ADD CONSTRAINT ck_settings_srs_retention CHECK (srs_desired_retention IN (0.900, 0.930, 0.950));

ALTER TABLE anki_card_progress
  ADD COLUMN fsrs_stability DOUBLE,
  ADD COLUMN fsrs_difficulty DOUBLE,
  ADD COLUMN fsrs_elapsed_days INTEGER,
  ADD COLUMN fsrs_scheduled_days INTEGER,
  ADD COLUMN fsrs_step INTEGER,
  ADD COLUMN fsrs_algorithm VARCHAR(20),
  ADD COLUMN first_reviewed_at DATETIME(6),
  ADD COLUMN last_rating VARCHAR(20);

ALTER TABLE anki_review_logs
  ADD COLUMN client_review_id BINARY(16),
  ADD COLUMN result_progress LONGTEXT;

CREATE UNIQUE INDEX ux_anki_review_user_client
  ON anki_review_logs(user_id, client_review_id);

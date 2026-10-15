CREATE TABLE builtin_review_logs (
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16) NOT NULL,
  request_id BINARY(16) NOT NULL,
  card_id VARCHAR(200) NOT NULL,
  deck_type VARCHAR(20) NOT NULL,
  rating VARCHAR(10) NOT NULL,
  reviewed_at TIMESTAMP(6) NOT NULL,
  predicted_recall DOUBLE,
  elapsed_days DOUBLE,
  time_valid BOOLEAN NOT NULL,
  scheduler_version VARCHAR(80),
  telemetry LONGTEXT NOT NULL,
  result_card LONGTEXT NOT NULL,
  CONSTRAINT ux_builtin_review_request UNIQUE (user_id, request_id),
  CONSTRAINT fk_builtin_review_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
  INDEX ix_builtin_review_history (user_id, deck_type, card_id, reviewed_at)
);

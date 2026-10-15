-- Nullable additions preserve historical reviews without inventing timing data.
ALTER TABLE anki_review_logs
  ADD COLUMN recall_time_ms INTEGER,
  ADD COLUMN decision_time_ms INTEGER,
  ADD COLUMN is_time_valid BOOLEAN,
  ADD COLUMN shown_at TIMESTAMP(6) NULL,
  ADD COLUMN revealed_at TIMESTAMP(6) NULL,
  ADD COLUMN rated_at TIMESTAMP(6) NULL,
  ADD COLUMN elapsed_days DOUBLE,
  ADD COLUMN predicted_recall DOUBLE,
  ADD COLUMN scheduler_version VARCHAR(80);

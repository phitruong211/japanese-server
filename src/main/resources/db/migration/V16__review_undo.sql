ALTER TABLE builtin_review_logs ADD COLUMN before_card LONGTEXT NULL;
ALTER TABLE builtin_review_logs ADD COLUMN undone_at DATETIME(6) NULL;
ALTER TABLE anki_review_logs ADD COLUMN before_progress LONGTEXT NULL;
ALTER TABLE anki_review_logs ADD COLUMN progress_existed BOOLEAN NULL;
ALTER TABLE anki_review_logs ADD COLUMN undone_at DATETIME(6) NULL;

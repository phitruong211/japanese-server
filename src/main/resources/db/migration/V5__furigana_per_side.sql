ALTER TABLE user_settings ADD COLUMN show_furigana_front BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_settings ADD COLUMN show_furigana_back BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE user_settings SET show_furigana_front = show_furigana, show_furigana_back = show_furigana;

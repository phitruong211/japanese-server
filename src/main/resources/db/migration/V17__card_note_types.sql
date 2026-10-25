-- Note types and per-card field values (docs/deck-format-v1.md). Additive: existing columns stay as the derived projection,
-- and cards without field_values keep working; clients derive their fields from the legacy columns until they are edited.
ALTER TABLE cards ADD COLUMN note_type VARCHAR(32) NULL, ADD COLUMN field_values JSON NULL;

-- Matches active-card filtering and the stable order used by /decks/{id}/cards.
CREATE INDEX ix_cards_deck_active_position ON cards (deck_id, deleted_at, position, id);

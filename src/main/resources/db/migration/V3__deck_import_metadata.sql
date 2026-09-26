alter table decks add column source varchar(30), add column source_ref varchar(1000), add column source_sheet varchar(255), add column tags json, add column import_key varchar(128), add column import_hash varchar(64);
update decks set source = case when source_type = 'IMPORT' then 'IMPORT' when source_type = 'SYSTEM' then 'BUILT_IN' else 'MANUAL' end;
create unique index ux_deck_import_key on decks(owner_id, import_key);
create index ix_decks_owner_active_updated on decks(owner_id, deleted_at, updated_at);
create index ix_cards_deck_active_kind_position on cards(deck_id, deleted_at, kind, position);
create index ix_progress_user_state_due_card on anki_card_progress(user_id, state, due_at, card_id);

alter table cards add column source varchar(30), add column source_ref varchar(1000), add column source_sheet varchar(255);
update cards c join decks d on d.id = c.deck_id set c.source = d.source;

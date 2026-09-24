alter table decks add column position integer not null default 0 after card_count;
alter table decks add column template_config json null after position;
update decks set template_config = json_object() where template_config is null;
alter table decks modify column template_config json not null;
create index ix_decks_owner_position on decks(owner_id, position);

alter table deck_groups add column share_token varchar(36) null;
alter table deck_groups add column import_key varchar(128) null;
create unique index ux_deck_group_share_token on deck_groups(share_token);
create unique index ux_deck_group_import_key on deck_groups(owner_id, import_key);

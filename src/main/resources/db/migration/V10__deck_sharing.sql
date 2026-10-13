alter table decks add column share_token varchar(36) null;
create unique index ux_deck_share_token on decks(share_token);

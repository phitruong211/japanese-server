create table deck_groups (
 id binary(16) primary key,
 owner_id binary(16) not null,
 name varchar(200) not null,
 position integer not null default 0,
 created_at datetime(6) not null,
 updated_at datetime(6) not null,
 version bigint not null default 0,
 constraint fk_deck_groups_owner foreign key (owner_id) references app_users(id) on delete cascade
);
create index ix_deck_groups_owner_position on deck_groups(owner_id, position);
alter table decks add column group_id binary(16) null;
alter table decks add constraint fk_decks_group foreign key (group_id) references deck_groups(id) on delete set null;
create index ix_decks_owner_group_position on decks(owner_id, group_id, position);

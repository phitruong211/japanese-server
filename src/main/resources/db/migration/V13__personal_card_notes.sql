create table personal_card_notes (
    id binary(16) not null primary key,
    owner_id binary(16) not null,
    note_key varchar(64) not null,
    content text not null,
    revision bigint not null,
    updated_at timestamp(6) not null,
    constraint fk_personal_card_note_owner foreign key (owner_id) references app_users(id) on delete cascade,
    constraint ux_personal_card_note unique (owner_id, note_key)
);

alter table decks add column share_personal_notes boolean not null default false;
alter table deck_groups add column share_personal_notes boolean not null default false;

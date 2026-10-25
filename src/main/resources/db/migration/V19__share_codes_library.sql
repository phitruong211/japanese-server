-- Short share codes, link expiry, the public library and abuse reports (docs/deck-format-v1.md, phase 4).
alter table decks
    add column share_code char(8) null,
    add column share_expires_at timestamp(6) null,
    add column library_level varchar(3) null,
    add column published_at timestamp(6) null,
    add column download_count int not null default 0,
    add column moderation_status varchar(16) not null default 'ACTIVE',
    add column removed_reason varchar(500) null,
    add column removed_at timestamp(6) null;
create unique index ux_deck_share_code on decks(share_code);
create index ix_decks_library on decks(visibility, moderation_status, published_at);
alter table deck_groups
    add column share_code char(8) null,
    add column share_expires_at timestamp(6) null;
create unique index ux_group_share_code on deck_groups(share_code);
create table deck_reports (
    id binary(16) not null primary key,
    deck_id binary(16) not null,
    reporter_id binary(16) not null,
    reason varchar(16) not null,
    details varchar(500) null,
    status varchar(16) not null,
    created_at timestamp(6) not null,
    handled_at timestamp(6) null,
    handled_by binary(16) null,
    constraint fk_deck_report_deck foreign key (deck_id) references decks(id) on delete cascade,
    constraint fk_deck_report_reporter foreign key (reporter_id) references app_users(id) on delete cascade,
    constraint ux_deck_report_once unique (deck_id, reporter_id)
);
create index ix_deck_reports_status on deck_reports(status, created_at);

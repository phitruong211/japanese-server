create table app_users (
    id binary(16) primary key,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    display_name varchar(100) not null,
    avatar_url text,
    role varchar(30) not null default 'USER',
    status varchar(30) not null default 'ACTIVE',
    email_verified_at datetime(6),
    locale varchar(10) not null default 'vi-VN',
    timezone varchar(50) not null default 'Asia/Bangkok',
    last_login_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    version bigint not null default 0,
    constraint ck_users_role check (role in ('USER', 'ADMIN')),
    constraint ck_users_status check (status in ('ACTIVE', 'LOCKED', 'DISABLED'))
);
create unique index ux_users_email on app_users (email);

create table user_settings (
    user_id binary(16) primary key,
    theme varchar(30) not null default 'light',
    font_size varchar(20) not null default 'medium',
    show_furigana boolean not null default true,
    auto_play_audio boolean not null default false,
    daily_goal integer not null default 20,
    anki_session_minutes integer not null default 0,
    reduced_motion boolean not null default false,
    updated_at datetime(6) not null,
    constraint ck_settings_daily_goal check (daily_goal between 1 and 1000),
    constraint ck_settings_anki_minutes check (anki_session_minutes between 0 and 180),
    constraint fk_settings_user foreign key (user_id) references app_users(id) on delete cascade
);

create table refresh_tokens (
    id binary(16) primary key,
    user_id binary(16) not null,
    token_hash char(64) not null unique,
    device_name varchar(200),
    expires_at datetime(6) not null,
    revoked_at datetime(6),
    created_at datetime(6) not null,
    constraint fk_refresh_user foreign key (user_id) references app_users(id) on delete cascade
);
create index ix_refresh_tokens_user on refresh_tokens(user_id);

create table decks (
    id binary(16) primary key,
    owner_id binary(16) not null,
    name varchar(200) not null,
    description text,
    source_type varchar(30) not null default 'MANUAL',
    source_name varchar(255),
    import_format varchar(20),
    visibility varchar(20) not null default 'PRIVATE',
    card_count integer not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6),
    version bigint not null default 0,
    constraint ck_decks_source_type check (source_type in ('MANUAL', 'IMPORT', 'SYSTEM')),
    constraint ck_decks_visibility check (visibility in ('PRIVATE', 'PUBLIC')),
    constraint ck_decks_card_count check (card_count >= 0),
    constraint fk_decks_owner foreign key (owner_id) references app_users(id) on delete cascade
);
create index ix_decks_owner_updated on decks(owner_id, updated_at desc);

create table cards (
    id binary(16) primary key,
    deck_id binary(16) not null,
    front text not null,
    back text not null,
    reading text,
    notes text,
    kind varchar(30) not null default 'GENERAL',
    position integer not null default 0,
    external_id varchar(255),
    extra_data json not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6),
    version bigint not null default 0,
    constraint ck_cards_kind check (kind in ('VOCABULARY', 'KANJI', 'GRAMMAR', 'GENERAL')),
    constraint ck_cards_position check (position >= 0),
    constraint fk_cards_deck foreign key (deck_id) references decks(id) on delete cascade
);
create index ix_cards_deck_position on cards(deck_id, position);

create table anki_card_progress (
    id binary(16) primary key,
    user_id binary(16) not null,
    card_id binary(16) not null,
    state varchar(20) not null default 'NEW',
    ease_factor numeric(4,2) not null default 2.50,
    interval_minutes integer,
    interval_days integer,
    due_at datetime(6) not null,
    repetitions integer not null default 0,
    lapses integer not null default 0,
    last_reviewed_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    version bigint not null default 0,
    constraint ux_anki_progress_user_card unique(user_id, card_id),
    constraint ck_anki_state check (state in ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    constraint ck_anki_ease check (ease_factor >= 1.30),
    constraint ck_anki_counts check (repetitions >= 0 and lapses >= 0),
    constraint fk_anki_progress_user foreign key (user_id) references app_users(id) on delete cascade,
    constraint fk_anki_progress_card foreign key (card_id) references cards(id) on delete cascade
);
create index ix_anki_progress_due on anki_card_progress(user_id, due_at);

create table anki_review_logs (
    id binary(16) primary key,
    user_id binary(16) not null,
    card_id binary(16) not null,
    rating varchar(20) not null,
    previous_state varchar(20) not null,
    new_state varchar(20) not null,
    previous_interval_days integer,
    new_interval_days integer,
    response_time_ms integer,
    reviewed_at datetime(6) not null,
    constraint ck_review_rating check (rating in ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    constraint ck_review_response_time check (response_time_ms is null or response_time_ms >= 0),
    constraint fk_anki_logs_user foreign key (user_id) references app_users(id) on delete cascade,
    constraint fk_anki_logs_card foreign key (card_id) references cards(id) on delete cascade
);
create index ix_anki_logs_user_reviewed on anki_review_logs(user_id, reviewed_at desc);

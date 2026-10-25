-- Fonts a user uploaded for their own decks. Private to the owner; metadata and bytes are split so listing never reads blobs.
create table user_fonts (
    id binary(16) not null primary key,
    owner_id binary(16) not null,
    name varchar(60) not null,
    format varchar(10) not null,
    size_bytes int not null,
    sha256 char(64) not null,
    created_at timestamp(6) not null,
    constraint fk_user_font_owner foreign key (owner_id) references app_users(id) on delete cascade,
    constraint ux_user_font_hash unique (owner_id, sha256)
);
create index ix_user_fonts_owner on user_fonts(owner_id, created_at);
create table user_font_files (
    font_id binary(16) not null primary key,
    data longblob not null,
    constraint fk_user_font_file foreign key (font_id) references user_fonts(id) on delete cascade
);

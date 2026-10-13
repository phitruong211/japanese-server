create table dictionary_datasets (
    id varchar(64) primary key,
    status varchar(20) not null,
    imported_at datetime(6) not null,
    entry_count integer not null,
    source_url varchar(500) not null
);
create table dictionary_catalog (
    id integer primary key,
    dataset_id varchar(64) not null
);
create table dictionary_entries (
    id varchar(100) primary key,
    dataset_id varchar(64) not null,
    entry_key varchar(40) not null,
    common_word boolean not null,
    payload longtext not null,
    constraint ux_dictionary_entry unique (dataset_id, entry_key)
);
create table dictionary_forms (
    id varchar(64) primary key,
    dataset_id varchar(64) not null,
    entry_key varchar(40) not null,
    normalized varchar(255) collate utf8mb4_bin not null,
    form_type varchar(10) not null
);
create index ix_dictionary_form_search on dictionary_forms(dataset_id, normalized, form_type, entry_key);
create table dictionary_tokens (
    id varchar(64) primary key,
    dataset_id varchar(64) not null,
    entry_key varchar(40) not null,
    token varchar(100) collate utf8mb4_bin not null
);
create index ix_dictionary_token_search on dictionary_tokens(dataset_id, token, entry_key);
create table dictionary_write_requests (
    id varchar(180) primary key,
    request_hash varchar(64) not null,
    result_payload text not null,
    created_at datetime(6) not null
);
alter table cards add column dictionary_identity varchar(64);
alter table cards add column dictionary_provenance text;
create index ix_cards_dictionary_identity on cards(deck_id, dictionary_identity, deleted_at);

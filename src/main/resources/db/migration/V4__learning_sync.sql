create table learning_states (
 user_id binary(16) primary key,
 revision bigint not null,
 payload longtext not null,
 constraint fk_learning_user foreign key (user_id) references app_users(id) on delete cascade
);
create table learning_migration_receipts (
 id binary(16) primary key,
 user_id binary(16) not null,
 request_key varchar(100) not null,
 fingerprint varchar(64) not null,
 payload longtext not null,
 constraint ux_learning_request unique (user_id, request_key),
 constraint fk_learning_receipt_user foreign key (user_id) references app_users(id) on delete cascade
);

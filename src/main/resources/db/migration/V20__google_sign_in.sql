-- Google sign-in: accounts may link a Google identity and may have no password.
alter table app_users add column google_sub varchar(255) null;
create unique index ux_app_users_google_sub on app_users (google_sub);
alter table app_users modify password_hash varchar(255) null;

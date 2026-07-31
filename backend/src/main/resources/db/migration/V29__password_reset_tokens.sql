-- Phase 15 slice: password-reset request/complete flow with hashed single-use tokens.

create table password_reset_token (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user (id) on delete cascade,
    token_hash  text not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now(),
    constraint password_reset_token_token_hash_key unique (token_hash)
);

create unique index password_reset_token_active_user_key
    on password_reset_token (user_id)
    where consumed_at is null;


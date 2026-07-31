-- Phase 15 slice: owner-registration email verification.
-- Public registration now creates a PENDING_EMAIL_VERIFICATION account until the
-- email-link token is redeemed.

alter table app_user
    drop constraint app_user_status_check;

alter table app_user
    add constraint app_user_status_check check (status in ('ACTIVE', 'SUSPENDED', 'PENDING_EMAIL_VERIFICATION'));

create table email_verification_token (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user (id) on delete cascade,
    token_hash  text not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now(),
    constraint email_verification_token_token_hash_key unique (token_hash)
);

create unique index email_verification_token_active_user_key
    on email_verification_token (user_id)
    where consumed_at is null;


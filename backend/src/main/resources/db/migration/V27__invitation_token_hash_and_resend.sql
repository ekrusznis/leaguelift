-- Phase 15 slice 1 foundation: hashed invitation-token lookup and token rotation.
-- Keeps the legacy `token` column for now so existing constraints/indexes and
-- rollback-free forward migrations stay stable, but it no longer needs to hold
-- the raw accept token value.

alter table invitation
    add column token_hash text;

-- Backfill from historical rows created before token hashing was introduced.
update invitation
set token_hash = encode(digest(token, 'sha256'), 'hex')
where token_hash is null;

alter table invitation
    alter column token_hash set not null;

create unique index invitation_token_hash_key on invitation (token_hash);


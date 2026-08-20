-- Founder-directed (2026-08-20, launch-readiness pass): a guardian-level default for
-- whether newly uploaded household media starts Public or stays Household-only,
-- instead of always defaulting to private and requiring an explicit "Release
-- publicly" action every time. Still opt-in per account — default is PRIVATE.

alter table user_preference
    add column default_media_visibility text not null default 'PRIVATE'
        check (default_media_visibility in ('PRIVATE', 'PUBLIC'));

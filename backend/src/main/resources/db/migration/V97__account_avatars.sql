-- Personal account avatar (nav bar + Settings), independent of the org-scoped media
-- pipeline (V9) and the household-adult/participant PROFILE_PHOTO slot (V31, ADR-045):
-- this is a self-service, org-independent identity image every app_user has, including
-- Platform Admin accounts with no organization membership at all.
--
-- avatar_object_key: private Spaces/S3 key for an uploaded photo, null until one is
-- confirmed. avatar_seed/avatar_style: an explicit user choice for the generated
-- fallback avatar shown when no photo is uploaded; both null means "derive from the
-- user's own id and a fixed default style" (resolved in application code, not SQL), so
-- no backfill is needed for existing rows.
alter table app_user
    add column avatar_object_key text,
    add column avatar_seed text,
    add column avatar_style text;

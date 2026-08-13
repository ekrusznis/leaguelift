-- Fixes a gap from the household media center (Track 5, 2026-08-13): HOUSEHOLD_MEDIA
-- was added to the application-layer MediaUsageSlot enum but this migration was
-- missed, so a real Postgres insert would have failed both the usage_slot check
-- constraint and (worse) the "one active row per entity+slot" unique index below,
-- which was never told to exclude HOUSEHOLD_MEDIA the way V19 already excludes
-- DOCUMENT for the same "this is a gallery, not one replaceable slot" reason.
--
-- Also widens the same pipeline for Help Center article attachments (Track 4,
-- 2026-08-13): a new SUPPORT_ARTICLE entity type and ARTICLE_ATTACHMENT usage slot,
-- same "gallery, not one slot" shape as DOCUMENT/HOUSEHOLD_MEDIA.

alter table media_asset drop constraint media_asset_usage_slot_check;
alter table media_asset add constraint media_asset_usage_slot_check
    check (intended_usage_slot in
        ('LOGO', 'COVER', 'PROFILE_PHOTO', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT', 'HOUSEHOLD_MEDIA', 'ARTICLE_ATTACHMENT'));

alter table media_assignment drop constraint media_assignment_entity_type_check;
alter table media_assignment add constraint media_assignment_entity_type_check
    check (entity_type in
        ('ORGANIZATION', 'TEAM', 'TOURNAMENT', 'HOUSEHOLD_ADULT', 'PARTICIPANT',
         'PRODUCT', 'SPONSOR', 'HOUSEHOLD', 'SUPPORT_ARTICLE'));

alter table media_assignment drop constraint media_assignment_usage_slot_check;
alter table media_assignment add constraint media_assignment_usage_slot_check
    check (usage_slot in
        ('LOGO', 'COVER', 'PROFILE_PHOTO', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT', 'HOUSEHOLD_MEDIA', 'ARTICLE_ATTACHMENT'));

alter table media_assignment drop constraint media_assignment_entity_slot_check;
alter table media_assignment add constraint media_assignment_entity_slot_check
    check (
        (entity_type = 'ORGANIZATION' and usage_slot in ('LOGO', 'COVER', 'DOCUMENT')) or
        (entity_type in ('TEAM', 'TOURNAMENT') and usage_slot in ('LOGO', 'COVER')) or
        (entity_type in ('HOUSEHOLD_ADULT', 'PARTICIPANT') and usage_slot = 'PROFILE_PHOTO') or
        (entity_type = 'PRODUCT' and usage_slot = 'PRODUCT_DESIGN') or
        (entity_type = 'SPONSOR' and usage_slot = 'SPONSOR_LOGO') or
        (entity_type = 'HOUSEHOLD' and usage_slot in ('DOCUMENT', 'HOUSEHOLD_MEDIA')) or
        (entity_type = 'SUPPORT_ARTICLE' and usage_slot = 'ARTICLE_ATTACHMENT')
    );

drop index media_assignment_active_slot_idx;
create unique index media_assignment_active_slot_idx on media_assignment (entity_type, entity_id, usage_slot)
    where publication_status <> 'RETIRED' and usage_slot not in ('DOCUMENT', 'HOUSEHOLD_MEDIA', 'ARTICLE_ATTACHMENT');

-- Fixed-UUID sentinel organization owning platform-level media (Help Center article
-- attachments) that has no real owning organization. See PlatformOrganization.kt.
insert into organization (id, name, slug, organization_type, status)
values ('00000000-0000-0000-0000-000000000001', 'Rally26 Platform', 'rally26-platform', 'OTHER', 'ACTIVE')
on conflict (id) do nothing;

-- Phase 16 slice 3: widen the existing media pipeline for team/tournament branding
-- and organization-owned adult/participant profile photos. No separate blob or
-- profile-photo table is introduced; all files retain the V9 signed-upload,
-- validation, assignment, retirement, and visibility lifecycle.

alter table media_asset drop constraint media_asset_usage_slot_check;
alter table media_asset add constraint media_asset_usage_slot_check
    check (intended_usage_slot in
        ('LOGO', 'COVER', 'PROFILE_PHOTO', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT'));

alter table media_assignment drop constraint media_assignment_entity_type_check;
alter table media_assignment add constraint media_assignment_entity_type_check
    check (entity_type in
        ('ORGANIZATION', 'TEAM', 'TOURNAMENT', 'HOUSEHOLD_ADULT', 'PARTICIPANT',
         'PRODUCT', 'SPONSOR', 'HOUSEHOLD'));

alter table media_assignment drop constraint media_assignment_usage_slot_check;
alter table media_assignment add constraint media_assignment_usage_slot_check
    check (usage_slot in
        ('LOGO', 'COVER', 'PROFILE_PHOTO', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT'));

-- Protect the polymorphic entity/slot contract at the database boundary as well as
-- in MediaEntityAccessService. ORGANIZATION supports both branding and its existing
-- document list; every other entity type has one narrow media purpose.
alter table media_assignment add constraint media_assignment_entity_slot_check
    check (
        (entity_type = 'ORGANIZATION' and usage_slot in ('LOGO', 'COVER', 'DOCUMENT')) or
        (entity_type in ('TEAM', 'TOURNAMENT') and usage_slot in ('LOGO', 'COVER')) or
        (entity_type in ('HOUSEHOLD_ADULT', 'PARTICIPANT') and usage_slot = 'PROFILE_PHOTO') or
        (entity_type = 'PRODUCT' and usage_slot = 'PRODUCT_DESIGN') or
        (entity_type = 'SPONSOR' and usage_slot = 'SPONSOR_LOGO') or
        (entity_type = 'HOUSEHOLD' and usage_slot = 'DOCUMENT')
    );

-- Widens the media pipeline's entity_type/usage_slot check constraints to allow
-- PRODUCT/PRODUCT_DESIGN (Phase 4 slice 1, store product design uploads) — exactly
-- the widening V9__media.sql's own comment anticipated: "deliberately narrow ...
-- widened with a later ALTER TABLE ... when that slice begins." Team/tournament
-- logos remain out of scope; only PRODUCT is added here.

alter table media_asset drop constraint media_asset_usage_slot_check;
alter table media_asset add constraint media_asset_usage_slot_check
    check (intended_usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN'));

alter table media_assignment drop constraint media_assignment_entity_type_check;
alter table media_assignment add constraint media_assignment_entity_type_check
    check (entity_type in ('ORGANIZATION', 'PRODUCT'));

alter table media_assignment drop constraint media_assignment_usage_slot_check;
alter table media_assignment add constraint media_assignment_usage_slot_check
    check (usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN'));

-- Phase 7 completion: household/org document storage, generalizing the media
-- pipeline beyond images (DESIGN-DOC.md section 13, section 11.3). Reuses
-- media_asset (upload/validation) and media_assignment (entity link) as-is rather
-- than duplicating a parallel storage table — the only schema change media_asset/
-- media_assignment need is widening their check constraints for a DOCUMENT usage
-- slot and a HOUSEHOLD entity type, same pattern V14 used to widen for products.
--
-- Unlike LOGO/COVER/PRODUCT_DESIGN/SPONSOR_LOGO, a DOCUMENT slot is deliberately
-- excluded from the "at most one active assignment per entity+slot" unique index —
-- an org or household needs a *list* of documents (waivers, forms, uploads), not
-- one replaceable slot. The application layer (DocumentService) never calls
-- MediaAssignmentService.assign()'s retire-previous-on-reassign path for documents;
-- it inserts additional rows directly via MediaAssignmentRepository instead.

alter table media_asset drop constraint media_asset_usage_slot_check;
alter table media_asset add constraint media_asset_usage_slot_check
    check (intended_usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT'));

alter table media_assignment drop constraint media_assignment_entity_type_check;
alter table media_assignment add constraint media_assignment_entity_type_check
    check (entity_type in ('ORGANIZATION', 'PRODUCT', 'SPONSOR', 'HOUSEHOLD'));

alter table media_assignment drop constraint media_assignment_usage_slot_check;
alter table media_assignment add constraint media_assignment_usage_slot_check
    check (usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN', 'SPONSOR_LOGO', 'DOCUMENT'));

drop index media_assignment_active_slot_idx;
create unique index media_assignment_active_slot_idx on media_assignment (entity_type, entity_id, usage_slot)
    where publication_status <> 'RETIRED' and usage_slot <> 'DOCUMENT';

-- One row per (document, guardian) — a guardian "signs" a household document at
-- most once. household_adult_id (not just user_id) is the record of *who*
-- acknowledged, consistent with guardian_relationship treating household_adult as
-- the authoritative person and app_user as just their login; acknowledged_by_user_id
-- is kept alongside for audit (which login session performed it).
create table document_acknowledgment (
    id                     uuid primary key default gen_random_uuid(),
    organization_id        uuid not null references organization (id),
    media_assignment_id    uuid not null references media_assignment (id),
    household_id           uuid not null references household (id),
    household_adult_id     uuid not null references household_adult (id),
    acknowledged_by_user_id uuid not null references app_user (id),
    acknowledged_at        timestamptz not null default now(),
    created_at             timestamptz not null default now(),
    constraint document_acknowledgment_unique unique (media_assignment_id, household_adult_id)
);

create index document_acknowledgment_assignment_idx on document_acknowledgment (media_assignment_id);
create index document_acknowledgment_household_idx on document_acknowledgment (household_id);
create index document_acknowledgment_organization_idx on document_acknowledgment (organization_id);

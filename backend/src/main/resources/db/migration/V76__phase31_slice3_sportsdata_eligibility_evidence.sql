-- Phase 31 slice 31.3 (DESIGN-DOC.md section 14.1L section 30.4/30.5): widen sports_data_mapping's
-- entity_type check constraint to add ELIGIBILITY_EVIDENCE, so a provider's waiver/registration
-- evidence records can flow through the same generic sports-data preview pipeline that already
-- discovers ORGANIZATION/TEAM/PARTICIPANT/ROSTER_MEMBERSHIP/EVENT records. Preview-only, same as
-- every other sports-data entity type today (commit_allowed stays false pending a registered and
-- verified TeamSnap/SportsEngine developer application, see V73's own comment) -- this migration
-- only makes the entity type storable, it does not add a commit path into eligibility_evidence.

alter table sports_data_mapping drop constraint sports_data_entity_type_check;
alter table sports_data_mapping add constraint sports_data_entity_type_check
    check (entity_type in ('ORGANIZATION', 'TEAM', 'PARTICIPANT', 'ROSTER_MEMBERSHIP', 'EVENT', 'ELIGIBILITY_EVIDENCE'));

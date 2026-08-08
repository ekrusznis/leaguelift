-- Phase 27.1: reshape the legacy audit table before the role-scoped History Viewer depends on it.
-- One logical audit_event table is range-partitioned monthly by created_at.
-- Existing rows are preserved, and the original six-argument AuditService call remains source-compatible.

alter table audit_event rename to audit_event_legacy;
alter index if exists audit_event_pkey rename to audit_event_legacy_pkey;
alter index if exists audit_event_organization_id_idx rename to audit_event_legacy_organization_id_idx;
alter index if exists audit_event_entity_idx rename to audit_event_legacy_entity_idx;
alter index if exists audit_event_created_at_idx rename to audit_event_legacy_created_at_idx;

create table audit_event (
    id               uuid not null default gen_random_uuid(),
    actor_user_id    uuid references app_user (id),
    actor_type       text not null default 'SYSTEM',
    organization_id  uuid references organization (id),
    team_id          uuid references team (id),
    household_id     uuid references household (id),
    participant_id   uuid references participant (id),
    target_user_id   uuid references app_user (id),
    action           text not null,
    result           text not null default 'SUCCESS',
    entity_type      text not null,
    entity_id        uuid not null,
    summary          text not null,
    metadata         jsonb not null default '{}'::jsonb,
    correlation_id   uuid,
    created_at       timestamptz not null default now(),
    constraint audit_event_pkey primary key (created_at, id),
    constraint audit_event_actor_type_check check (actor_type in ('USER', 'SYSTEM', 'PROVIDER')),
    constraint audit_event_result_check check (result in ('SUCCESS', 'FAILURE', 'DENIED', 'PARTIAL')),
    constraint audit_event_user_actor_check check (actor_type <> 'USER' or actor_user_id is not null)
) partition by range (created_at);

create table audit_event_2026_01 partition of audit_event for values from ('2026-01-01 00:00:00+00') to ('2026-02-01 00:00:00+00');
create table audit_event_2026_02 partition of audit_event for values from ('2026-02-01 00:00:00+00') to ('2026-03-01 00:00:00+00');
create table audit_event_2026_03 partition of audit_event for values from ('2026-03-01 00:00:00+00') to ('2026-04-01 00:00:00+00');
create table audit_event_2026_04 partition of audit_event for values from ('2026-04-01 00:00:00+00') to ('2026-05-01 00:00:00+00');
create table audit_event_2026_05 partition of audit_event for values from ('2026-05-01 00:00:00+00') to ('2026-06-01 00:00:00+00');
create table audit_event_2026_06 partition of audit_event for values from ('2026-06-01 00:00:00+00') to ('2026-07-01 00:00:00+00');
create table audit_event_2026_07 partition of audit_event for values from ('2026-07-01 00:00:00+00') to ('2026-08-01 00:00:00+00');
create table audit_event_2026_08 partition of audit_event for values from ('2026-08-01 00:00:00+00') to ('2026-09-01 00:00:00+00');
create table audit_event_2026_09 partition of audit_event for values from ('2026-09-01 00:00:00+00') to ('2026-10-01 00:00:00+00');
create table audit_event_2026_10 partition of audit_event for values from ('2026-10-01 00:00:00+00') to ('2026-11-01 00:00:00+00');
create table audit_event_2026_11 partition of audit_event for values from ('2026-11-01 00:00:00+00') to ('2026-12-01 00:00:00+00');
create table audit_event_2026_12 partition of audit_event for values from ('2026-12-01 00:00:00+00') to ('2027-01-01 00:00:00+00');
create table audit_event_2027_01 partition of audit_event for values from ('2027-01-01 00:00:00+00') to ('2027-02-01 00:00:00+00');
create table audit_event_2027_02 partition of audit_event for values from ('2027-02-01 00:00:00+00') to ('2027-03-01 00:00:00+00');
create table audit_event_2027_03 partition of audit_event for values from ('2027-03-01 00:00:00+00') to ('2027-04-01 00:00:00+00');
create table audit_event_2027_04 partition of audit_event for values from ('2027-04-01 00:00:00+00') to ('2027-05-01 00:00:00+00');
create table audit_event_2027_05 partition of audit_event for values from ('2027-05-01 00:00:00+00') to ('2027-06-01 00:00:00+00');
create table audit_event_2027_06 partition of audit_event for values from ('2027-06-01 00:00:00+00') to ('2027-07-01 00:00:00+00');
create table audit_event_2027_07 partition of audit_event for values from ('2027-07-01 00:00:00+00') to ('2027-08-01 00:00:00+00');
create table audit_event_2027_08 partition of audit_event for values from ('2027-08-01 00:00:00+00') to ('2027-09-01 00:00:00+00');
create table audit_event_2027_09 partition of audit_event for values from ('2027-09-01 00:00:00+00') to ('2027-10-01 00:00:00+00');
create table audit_event_2027_10 partition of audit_event for values from ('2027-10-01 00:00:00+00') to ('2027-11-01 00:00:00+00');
create table audit_event_2027_11 partition of audit_event for values from ('2027-11-01 00:00:00+00') to ('2027-12-01 00:00:00+00');
create table audit_event_2027_12 partition of audit_event for values from ('2027-12-01 00:00:00+00') to ('2028-01-01 00:00:00+00');
create table audit_event_2028_01 partition of audit_event for values from ('2028-01-01 00:00:00+00') to ('2028-02-01 00:00:00+00');
create table audit_event_2028_02 partition of audit_event for values from ('2028-02-01 00:00:00+00') to ('2028-03-01 00:00:00+00');
create table audit_event_2028_03 partition of audit_event for values from ('2028-03-01 00:00:00+00') to ('2028-04-01 00:00:00+00');
create table audit_event_2028_04 partition of audit_event for values from ('2028-04-01 00:00:00+00') to ('2028-05-01 00:00:00+00');
create table audit_event_2028_05 partition of audit_event for values from ('2028-05-01 00:00:00+00') to ('2028-06-01 00:00:00+00');
create table audit_event_2028_06 partition of audit_event for values from ('2028-06-01 00:00:00+00') to ('2028-07-01 00:00:00+00');
create table audit_event_2028_07 partition of audit_event for values from ('2028-07-01 00:00:00+00') to ('2028-08-01 00:00:00+00');
create table audit_event_2028_08 partition of audit_event for values from ('2028-08-01 00:00:00+00') to ('2028-09-01 00:00:00+00');
create table audit_event_2028_09 partition of audit_event for values from ('2028-09-01 00:00:00+00') to ('2028-10-01 00:00:00+00');
create table audit_event_2028_10 partition of audit_event for values from ('2028-10-01 00:00:00+00') to ('2028-11-01 00:00:00+00');
create table audit_event_2028_11 partition of audit_event for values from ('2028-11-01 00:00:00+00') to ('2028-12-01 00:00:00+00');
create table audit_event_2028_12 partition of audit_event for values from ('2028-12-01 00:00:00+00') to ('2029-01-01 00:00:00+00');
create table audit_event_2029_01 partition of audit_event for values from ('2029-01-01 00:00:00+00') to ('2029-02-01 00:00:00+00');
create table audit_event_2029_02 partition of audit_event for values from ('2029-02-01 00:00:00+00') to ('2029-03-01 00:00:00+00');
create table audit_event_2029_03 partition of audit_event for values from ('2029-03-01 00:00:00+00') to ('2029-04-01 00:00:00+00');
create table audit_event_2029_04 partition of audit_event for values from ('2029-04-01 00:00:00+00') to ('2029-05-01 00:00:00+00');
create table audit_event_2029_05 partition of audit_event for values from ('2029-05-01 00:00:00+00') to ('2029-06-01 00:00:00+00');
create table audit_event_2029_06 partition of audit_event for values from ('2029-06-01 00:00:00+00') to ('2029-07-01 00:00:00+00');
create table audit_event_2029_07 partition of audit_event for values from ('2029-07-01 00:00:00+00') to ('2029-08-01 00:00:00+00');
create table audit_event_2029_08 partition of audit_event for values from ('2029-08-01 00:00:00+00') to ('2029-09-01 00:00:00+00');
create table audit_event_2029_09 partition of audit_event for values from ('2029-09-01 00:00:00+00') to ('2029-10-01 00:00:00+00');
create table audit_event_2029_10 partition of audit_event for values from ('2029-10-01 00:00:00+00') to ('2029-11-01 00:00:00+00');
create table audit_event_2029_11 partition of audit_event for values from ('2029-11-01 00:00:00+00') to ('2029-12-01 00:00:00+00');
create table audit_event_2029_12 partition of audit_event for values from ('2029-12-01 00:00:00+00') to ('2030-01-01 00:00:00+00');
create table audit_event_2030_01 partition of audit_event for values from ('2030-01-01 00:00:00+00') to ('2030-02-01 00:00:00+00');
create table audit_event_2030_02 partition of audit_event for values from ('2030-02-01 00:00:00+00') to ('2030-03-01 00:00:00+00');
create table audit_event_2030_03 partition of audit_event for values from ('2030-03-01 00:00:00+00') to ('2030-04-01 00:00:00+00');
create table audit_event_2030_04 partition of audit_event for values from ('2030-04-01 00:00:00+00') to ('2030-05-01 00:00:00+00');
create table audit_event_2030_05 partition of audit_event for values from ('2030-05-01 00:00:00+00') to ('2030-06-01 00:00:00+00');
create table audit_event_2030_06 partition of audit_event for values from ('2030-06-01 00:00:00+00') to ('2030-07-01 00:00:00+00');
create table audit_event_2030_07 partition of audit_event for values from ('2030-07-01 00:00:00+00') to ('2030-08-01 00:00:00+00');
create table audit_event_2030_08 partition of audit_event for values from ('2030-08-01 00:00:00+00') to ('2030-09-01 00:00:00+00');
create table audit_event_2030_09 partition of audit_event for values from ('2030-09-01 00:00:00+00') to ('2030-10-01 00:00:00+00');
create table audit_event_2030_10 partition of audit_event for values from ('2030-10-01 00:00:00+00') to ('2030-11-01 00:00:00+00');
create table audit_event_2030_11 partition of audit_event for values from ('2030-11-01 00:00:00+00') to ('2030-12-01 00:00:00+00');
create table audit_event_2030_12 partition of audit_event for values from ('2030-12-01 00:00:00+00') to ('2031-01-01 00:00:00+00');
create table audit_event_default partition of audit_event default;

-- Access-pattern indexes for the Phase 27.2 role-scoped History Viewer.
-- PostgreSQL creates matching child indexes on every partition.
create index audit_event_organization_created_idx on audit_event (organization_id, created_at desc, id desc)
    where organization_id is not null;
create index audit_event_team_created_idx on audit_event (team_id, created_at desc, id desc)
    where team_id is not null;
create index audit_event_household_created_idx on audit_event (household_id, created_at desc, id desc)
    where household_id is not null;
create index audit_event_participant_created_idx on audit_event (participant_id, created_at desc, id desc)
    where participant_id is not null;
create index audit_event_actor_created_idx on audit_event (actor_user_id, created_at desc, id desc)
    where actor_user_id is not null;
create index audit_event_target_user_created_idx on audit_event (target_user_id, created_at desc, id desc)
    where target_user_id is not null;
create index audit_event_entity_created_idx on audit_event (entity_type, entity_id, created_at desc, id desc);
create index audit_event_action_created_idx on audit_event (action, created_at desc, id desc);
create index audit_event_result_created_idx on audit_event (result, created_at desc, id desc);
create index audit_event_correlation_idx on audit_event (correlation_id, created_at desc, id desc)
    where correlation_id is not null;
create index audit_event_created_brin_idx on audit_event using brin (created_at);

-- Visibility scope is security-sensitive. If a subordinate scope is supplied, prove that it
-- belongs to the same organization before accepting the event. Participant/household pairings
-- are also validated so later authorization queries can trust these columns directly.
create or replace function validate_audit_event_scope()
returns trigger
language plpgsql
as $$
declare
    scoped_organization_id uuid;
    participant_household_id uuid;
begin
    if new.team_id is not null then
        select organization_id into scoped_organization_id from team where id = new.team_id;
        if not found then
            raise exception 'audit team scope does not exist: %', new.team_id;
        end if;
        if new.organization_id is null or new.organization_id <> scoped_organization_id then
            raise exception 'audit team scope % does not belong to organization %', new.team_id, new.organization_id;
        end if;
    end if;

    if new.household_id is not null then
        select organization_id into scoped_organization_id from household where id = new.household_id;
        if not found then
            raise exception 'audit household scope does not exist: %', new.household_id;
        end if;
        if new.organization_id is null or new.organization_id <> scoped_organization_id then
            raise exception 'audit household scope % does not belong to organization %', new.household_id, new.organization_id;
        end if;
    end if;

    if new.participant_id is not null then
        select organization_id, household_id
          into scoped_organization_id, participant_household_id
          from participant
         where id = new.participant_id;
        if not found then
            raise exception 'audit participant scope does not exist: %', new.participant_id;
        end if;
        if new.organization_id is null or new.organization_id <> scoped_organization_id then
            raise exception 'audit participant scope % does not belong to organization %', new.participant_id, new.organization_id;
        end if;
        if new.household_id is not null and new.household_id <> participant_household_id then
            raise exception 'audit participant scope % does not belong to household %', new.participant_id, new.household_id;
        end if;
    end if;

    return new;
end;
$$;

create trigger audit_event_validate_scope_before_insert
before insert on audit_event
for each row execute function validate_audit_event_scope();

-- Preserve every existing event. Historical rows did not carry the richer Phase 27 scope,
-- so those fields remain null rather than being guessed from mutable current relationships.
insert into audit_event (
    id, actor_user_id, actor_type, organization_id, team_id, household_id, participant_id,
    target_user_id, action, result, entity_type, entity_id, summary, metadata, correlation_id, created_at
)
select
    id,
    actor_user_id,
    case when actor_user_id is null then 'SYSTEM' else 'USER' end,
    organization_id,
    null,
    null,
    null,
    null,
    action,
    'SUCCESS',
    entity_type,
    entity_id,
    action,
    metadata,
    null,
    created_at
from audit_event_legacy;

do $$
declare
    legacy_count bigint;
    migrated_count bigint;
begin
    select count(*) into legacy_count from audit_event_legacy;
    select count(*) into migrated_count from audit_event;
    if legacy_count <> migrated_count then
        raise exception 'audit_event migration count mismatch: legacy %, migrated %', legacy_count, migrated_count;
    end if;
end;
$$;

drop table audit_event_legacy;

-- Application audit history is append-only. Corrections are represented by a new audit event,
-- never by mutating or deleting the historical event.
create or replace function reject_audit_event_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'audit_event is append-only; % is not permitted', tg_op;
end;
$$;

create trigger audit_event_reject_update
before update on audit_event
for each row execute function reject_audit_event_mutation();

create trigger audit_event_reject_delete
before delete on audit_event
for each row execute function reject_audit_event_mutation();

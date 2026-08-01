-- Phase 16 slice 6: preview-confirmed season rollover history.
-- A completed row records only the selected setup-copy options, destination identity,
-- and counts. Financial history, event/RSVP data, credentials, guardian relationships,
-- and consent state are never copied into or represented by this workflow.

create table season_rollover_run (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    source_team_id        uuid not null references team (id),
    destination_team_id   uuid not null references team (id),
    confirmation_hash     char(64) not null,
    archive_source_team   boolean not null,
    copy_roster           boolean not null,
    copy_staff            boolean not null,
    copy_branding         boolean not null,
    roster_copied_count   integer not null default 0,
    staff_copied_count    integer not null default 0,
    branding_copied_count integer not null default 0,
    executed_by_user_id   uuid not null references app_user (id),
    created_at            timestamptz not null default now(),
    constraint season_rollover_counts_nonnegative_check check (
        roster_copied_count >= 0 and staff_copied_count >= 0 and branding_copied_count >= 0
    ),
    constraint season_rollover_confirmation_unique unique (organization_id, confirmation_hash)
);

create index season_rollover_run_organization_idx
    on season_rollover_run (organization_id, created_at desc);
create index season_rollover_run_source_team_idx on season_rollover_run (source_team_id);
create index season_rollover_run_destination_team_idx on season_rollover_run (destination_team_id);

-- Phase 10 slice 1 (ADR-026): optional schedule-visibility layer — DESIGN-DOC.md
-- section 14.1A. Manual entry only this slice; provider/external_event_id/
-- external_sync_hash/connection_id reserve the import-identity seam Phase 12's
-- Connector Hub will actually populate.
--
-- An event always references organization_id; team_id is nullable so an
-- organization-level MEETING/OTHER event (not owned by any one team) is possible.
-- tournament_id is the parent for a tournament child event (check-in, pool games,
-- bracket games, etc.) — the tournament itself still owns the public-page identity,
-- per section 14.1A's "tournament remains the parent business/public-page entity."
--
-- start_at/end_at/arrival_at/meeting_at are all nullable: a freshly created
-- tournament child event may intentionally have TBD time until assigned later.
--
-- opponent_team_id (another LeagueLift team in the same org) and opponent_name
-- (free text) are both nullable and mutually exclusive in practice, never both
-- meaningfully set — enforced at the application layer, not a DB constraint, since
-- "neither set" is also valid (a practice/meeting has no opponent at all).
--
-- No stored "display title" column — DESIGN-DOC.md's example title
-- ("Eastside U12 Blue vs Northshore FC") is generated on read from
-- team/opponent_team/opponent_name, with the stored `title` column only used when
-- an admin supplies an explicit custom override (e.g. "State Cup Semifinal").
--
-- `visibility` (who can see it: PUBLIC/ORGANIZATION/TEAM) is a distinct concept
-- from `status`'s DRAFT value, which already gates "is this event published at
-- all" the same way `sponsorship_package.status`'s DRAFT/PUBLISHED does — no
-- separate "publication_status" column, collapsing what section 14.1A's prose
-- lists as two fields into the existing status lifecycle (see ADR-026).

create table event (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    team_id             uuid references team (id),
    tournament_id       uuid references tournament (id),
    opponent_team_id    uuid references team (id),
    opponent_name       text,
    event_type          text not null,
    title               text,
    description         text,
    status              text not null default 'DRAFT',
    start_at            timestamptz,
    end_at              timestamptz,
    arrival_at          timestamptz,
    meeting_at          timestamptz,
    timezone            text not null,
    venue_name          text,
    address             text,
    latitude            double precision,
    longitude           double precision,
    area                text,
    meeting_point       text,
    directions_notes    text,
    visibility          text not null default 'TEAM',
    source_type         text not null default 'MANUAL',
    provider            text,
    connection_id       text,
    external_event_id   text,
    external_sync_hash  text,
    source_updated_at   timestamptz,
    created_by_user_id  uuid not null references app_user (id),
    updated_by_user_id  uuid not null references app_user (id),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint event_type_check check (event_type in ('COMPETITION', 'TOURNAMENT', 'PRACTICE', 'MEETING', 'OTHER')),
    constraint event_status_check check (status in ('DRAFT', 'TENTATIVE', 'SCHEDULED', 'DELAYED', 'POSTPONED', 'CANCELLED', 'COMPLETED')),
    constraint event_visibility_check check (visibility in ('PUBLIC', 'ORGANIZATION', 'TEAM')),
    constraint event_source_type_check check (source_type in ('MANUAL', 'CSV_IMPORT', 'ICS_FEED', 'MAXPREPS', 'GAMECHANGER'))
);

create index event_organization_id_idx on event (organization_id);
create index event_team_id_idx on event (team_id);
create index event_tournament_id_idx on event (tournament_id);
create index event_start_at_idx on event (start_at);

-- Import-identity dedup (section 14.1A): only meaningful once provider is set —
-- every manually-created event has provider null and is exempt from this constraint.
create unique index event_external_identity_idx on event (provider, connection_id, external_event_id)
    where provider is not null;

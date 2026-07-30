-- Phase 12 slice 3 (ADR-033): slice 1's connect-an-ICS-feed flow didn't anticipate
-- what the poller actually needs. Two additions:
--
-- timezone: resolves a feed's floating (no UTC offset) DTSTART/DTEND values —
-- mirrors the CSV import connector's own per-upload timezone (ADR-032). Defaulted
-- to 'UTC' for any pre-existing row (pre-pilot, no real connections exist yet); the
-- application layer always supplies a real value going forward.
--
-- team_id: which team (if any) synced events belong to — mirrors CSV import's own
-- per-upload team scope (ADR-032). Null means org-wide, same convention as
-- event.team_id itself.
alter table event_source_connection add column timezone text not null default 'UTC';
alter table event_source_connection add column team_id uuid references team (id);

-- A synced ICS feed / CSV import no longer auto-overwrites a live event on every
-- scheduled run. Instead, a detected upstream change is staged here until a staff
-- member explicitly applies it (EventService.applySourceUpdate) — see event.provider's
-- existing source-owned-field model (ADR-034). pending_source_snapshot_json holds the
-- new parsed field values (same shape the poller/importer already writes on upsert);
-- pending_source_hash is compared against future poller runs so an unapplied change
-- isn't re-detected/re-staged every 30 minutes.
alter table event
    add column pending_source_snapshot_json text,
    add column pending_source_hash varchar(64);

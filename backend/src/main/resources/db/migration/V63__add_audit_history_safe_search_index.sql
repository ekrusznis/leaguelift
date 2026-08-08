-- Phase 27.2: keyword-search acceleration for the safe, non-sensitive audit search surface.
-- Metadata JSON is intentionally excluded. Actor/organization/team names remain joined/filterable
-- but are not copied into an immutable audit search document.
create extension if not exists pg_trgm;

create index audit_event_safe_search_trgm_idx
    on audit_event using gin (
        (lower(coalesce(summary, '') || ' ' || coalesce(action, '') || ' ' || coalesce(entity_type, ''))) gin_trgm_ops
    );

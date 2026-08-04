# ADR-048: Phase 16 Slice 6 — Preview-Confirmed Season Rollover

**Status:** Accepted and implemented
**Date:** 2026-08-01

## Context

Rally26's current sports structure has a `team.season` text field but no separate season aggregate. Phase 16's final slice requires rollover, archive, and selective-copy controls without inventing a broader league-registration or scheduling model. The operation is high-risk because a naive “clone team” could silently duplicate financial history, RSVP responses, credentials, guardian relationships, or consent assumptions.

## Decision

Implement rollover as a manager-only, team-to-team setup operation with two endpoints:

- `POST /api/v1/organizations/{organizationId}/season-rollovers/preview`
- `POST /api/v1/organizations/{organizationId}/season-rollovers/execute`

Preview is side-effect free and returns every selected item plus a SHA-256 confirmation hash over the normalized request, source team state, and selected source-row identities/timestamps. Execute recomputes the snapshot under repeatable-read isolation and refuses a stale hash. A completed `season_rollover_run` row makes retries with the same hash idempotent.

Every rollover creates one new active team with an explicit new name and season. Sport and contact email carry forward as team identity. The caller may independently select:

1. Active participant-team links. Participant and household records are reused, not duplicated; prior `joined_at` dates reset to null.
2. Explicit active `TEAM` role assignments. Organization owner/administrator inherited access is not materialized because it already applies automatically.
3. Active ready team `LOGO`/`COVER` assignments. Existing media assets are reused; uploaded files are not duplicated.
4. Source-team archive after successful copy.

The workflow records normal team create/archive audit events plus `season_rollover.executed` with IDs, flags, and counts.

## Hard exclusions

The operation never copies or mutates:

- fee assignments, balances, payments, adjustments, credits, ledger entries, payouts;
- orders, contributions, sponsorship purchases, refunds, or fulfillment;
- events, imported-event identities, event templates, or RSVP responses;
- households, guardians, invitations, credentials, athlete access, or consent/authorization state;
- public pages, campaigns, stores, products, documents, integrations, or provider connections.

Historical rows attached to the archived source team remain intact.

## Consequences

- Phase 16 is complete without introducing a speculative `season` aggregate.
- The workflow is intentionally one source team to one destination team; multi-team batch rollover can be added later only with pilot evidence.
- Archived team names remain reserved by the existing unique constraint, so the destination needs a distinct name.
- Roster membership is copied as a current setup decision, not as historical join-date data.
- Full local Gradle/test execution remains required after applying the changed-files package; the delivery environment performed frontend strict type-checking and direct JVM 17 compilation of all new production Kotlin files against the supplied application/dependency classpath.

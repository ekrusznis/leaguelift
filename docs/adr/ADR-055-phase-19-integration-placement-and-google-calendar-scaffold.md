# ADR-055: Phase 19 integration placement and Google Calendar scaffold

**Status:** Accepted
**Date:** 2026-08-02
**Phase:** 19 slices 19.3–19.4

## Context

ADR-054 established a generalized provider catalog, owner-scoped connection registry, encrypted credential references, and disabled OAuth lifecycle. The next slice must place those concepts in the application without suggesting that unverified providers are live.

The ownership boundary differs by provider:

- Google Calendar belongs to one authenticated user and must not be authorized once by an organization owner for every household or staff member.
- QuickBooks and sports-data providers belong to an organization and are managed by owners or administrators.
- Stripe, Printify, Resend, Twilio, DigitalOcean Spaces, and keyless Google Maps are Rally26-operated infrastructure. Customer users must never see or enter the platform credentials.

The existing standards-based ICS export is already real and must remain available even when Google OAuth is unavailable.

## Decision

### Application placement

Add `/app/integrations` as a universal personal destination and link it from the routed application shell and each authenticated dashboard context. Add a dedicated organization `Integrations` section for organization-managed providers and preserve existing CSV import and ICS feed workflows there. Add a Platform Admin-only provider-readiness panel to Integration Operations.

The Platform Admin panel reports sanitized configuration checks only. It never returns values and never describes configuration as a successful live-provider health check.

### Google Calendar ownership and settings

Model Google Calendar as a `USER`-owned OAuth connection. Add one optional selected writable destination calendar per connection and a durable mapping from `(connection_id, Rally26 event_id)` to Google calendar/event identity, ETag, export hash, sync state, and redacted error.

The first modeled direction is `RALLY26_TO_GOOGLE`. Automatic synchronization remains disabled. This slice supplies mapping and provider-client seams but no scheduler and no claim that events are being written in a non-stub environment.

### Provider-specific client seam

Add calendar discovery, event upsert, and event deletion interfaces. The deterministic local/test implementation exposes fixed writable and read-only calendars and deterministic event IDs. Outside explicit stub mode, every provider operation fails closed with `GOOGLE_CALENDAR_CLIENT_NOT_ACTIVATED` until the official Google client and credentialed contract tests are activated in Phase 20.

Calendar selection is accepted only when the connected provider account returns that calendar as writable.

### Personal connection lifecycle

Expose personal refresh, health-check, provider-revoke, and local-disconnect routes using ADR-054's encrypted-token lifecycle and user ownership checks. OAuth callbacks redirect to the owning personal or organization Integrations workspace with a sanitized result marker. Provider denial consumes the one-time state and records a disconnected pending connection, immutable connection event, and audit record.

Local disconnect and provider-confirmed revocation remain distinct operations; the UI must not claim remote revocation when only local access was removed.

### ICS fallback and documentation

Keep ICS download/Add to Calendar behavior available independently of Google readiness. Publish Help Center articles explaining Google Calendar availability and why organizations never enter Rally26 platform credentials.

### Included catalog-readiness hotfix

When a provider runtime is disabled, catalog readiness returns `NOT_CONFIGURED` without querying the adapter registry. The regression test verifies the short-circuit. This preserves fail-closed behavior and avoids requiring a provider adapter for an intentionally disabled runtime.

## Consequences

- Every authenticated persona has an accurate personal integration destination without broadening organization permissions.
- Owners and administrators see organization-owned connectors in one dedicated workspace, while platform infrastructure stays out of customer credential flows.
- Google Calendar can be activated later by adding the verified official client and credentials rather than redesigning ownership, OAuth, calendar selection, or event identity mapping.
- ICS remains the reliable fallback and no automatic or two-way Google synchronization is implied.
- QuickBooks, SportsEngine, GameChanger, MaxPreps, platform-provider hardening, and generalized sync/observability records remain Phase 19.5–19.8.

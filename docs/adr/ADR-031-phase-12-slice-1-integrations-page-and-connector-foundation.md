# ADR-031: Phase 12 Slice 1 — Integrations Page and Connector Foundation

## Status
Accepted

## Context

DESIGN-DOC.md section 17 splits every external connector into two categories:
**platform-operated** (LeagueLift holds the one shared credential — Stripe, Printify,
Spaces, Resend, Twilio, Google Maps/Calendar's stub-level keys — all already built,
no per-org UI) and **org-connected** (an individual organization connects its own
account — MaxPreps, GameChanger, SportsEngine, feeding the `EventProvider` seam
section 14.1A reserves via `Event.sourceType`/`provider`/`connectionId`/
`externalEventId`/`externalSyncHash`, built in Phase 10 slice 1 but never
populated). No Integrations settings page exists yet, and section 17 is explicit:
*"do not build the Integrations page or wire a specific org-connected provider
before [a real design] review... do not claim a connector is live until its terms,
access, and technical behavior are verified."*

Three founder decisions were confirmed before implementation (resolving open
questions #22-24 from section 19.3):

1. The first real org-connected connectors are **CSV import** and **ICS feed
   subscription** — not MaxPreps/GameChanger/SportsEngine, since this codebase has
   no verified vendor account or API terms for any of the latter three, while CSV/
   ICS need no vendor relationship at all.
2. MaxPreps/GameChanger/SportsEngine appear on the Integrations page as **disabled
   "coming soon" cards** — transparent roadmap communication, not omitted and not
   fake-wired.
3. Google Calendar stays ICS-only this phase; real OAuth two-way sync remains a
   later, separate decision (open question #23) requiring its own registered
   Google Cloud project and consent-screen review.

This slice builds the page and the connector foundation only — CSV parsing (slice
2) and the ICS feed's scheduled sync poller (slice 3) are separate slices.

## Decision

**1. A new `event_source_connection` table represents an ongoing, stateful
connection — not every connector needs a row in it.** `ICS_FEED` (and any future
real `MAXPREPS`/`GAMECHANGER` OAuth connection) is inherently stateful: a
subscribed URL or account token that persists and gets periodically re-synced, so
it needs somewhere to live (`organization_id`, `provider`, `label`, `feed_url`,
`status`, `last_synced_at`, `last_sync_status`, `last_sync_error`). `CSV_IMPORT` is
a one-time bulk action, not a connection — it gets no row in this table; slice 2's
upload endpoint writes `event` rows directly. Multiple connections per
organization are allowed (an org may reasonably want to subscribe to more than one
team's external ICS feed); a partial unique index prevents the same URL being
added twice while active.

**2. `event.connection_id` (already reserved as free text since Phase 10) stores
`event_source_connection.id.toString()` for ICS-sourced events.** This is exactly
what the column was reserved for — no schema change needed on `event` itself.
CSV-imported events (slice 2) will use a different, connection-less convention for
that same column, decided in slice 2 rather than pre-guessed here.

**3. Authorization reuses `MembershipService.requireManagerRole` (OWNER/
ADMINISTRATOR), not a new `AuthorizationService` capability constant.** Every other
organization-scoped settings action in this codebase (org profile updates, team/
tournament role grants, payout transfer triggering, invitation revocation) gates
through this same manager-role check directly — `AuthorizationService` has no
`hasOrganizationCapability`/`requireOrganizationCapability` method at all today;
`CapabilityRegistry.organizationCapabilities()` exists only to power `/me/contexts`
listing, not enforcement. Introducing a new capability constant used nowhere else
in the ORGANIZATION context would be inconsistent with how this codebase actually
authorizes organization-level settings today, not a genuine improvement.

**4. The Integrations page is a new section on the existing
`OrganizationDetailPage`**, matching how every other org-settings area (Branding,
Payouts, Profile, Teams, Fee Templates, etc.) is already just another `<section>`
on that one page — not a separate route, not a capability-gated nav item, since
none of the existing sections are gated that way either.

**5. The page shows three distinct card treatments**, not a single generic list:
platform-operated connectors as a static, read-only list (no connect/disconnect —
there's nothing an org does with these); ICS Feed as a real connect-by-URL form
plus a list of active connections with disconnect; MaxPreps/GameChanger/
SportsEngine **and** CSV import as disabled "coming soon" cards this slice — CSV's
card becomes real in slice 2, the other three remain disabled until a real vendor
relationship and verified terms exist. Showing CSV disabled now (rather than
omitted) is deliberately honest about sequencing: it signals "planned, not yet
wired" exactly like the vendor cards, rather than silently appearing mid-phase
with no visible history.

## Consequences

- No sync/polling logic exists yet — connecting an ICS feed this slice only
  validates the URL is well-formed and stores it; nothing consumes it until
  slice 3's scheduled poller ships. This is called out explicitly in the UI
  copy ("sync starts once available") so a connected-but-never-synced feed
  doesn't read as a bug.
- `event_source_connection.provider` only allows `ICS_FEED`, `MAXPREPS`,
  `GAMECHANGER` (not `CSV_IMPORT`, not `MANUAL`) — enforced by a check
  constraint, matching decision 1's "not every connector needs a row" rule
  structurally, not just by convention.
- The disabled MaxPreps/GameChanger/SportsEngine cards render from a plain
  static list in the frontend, not a database-backed "planned connector"
  registry — there's no product requirement yet for that to be dynamic/
  admin-editable, and building one would be speculative.

## Alternatives Considered

- **A generic `provider` column also accepting `CSV_IMPORT`, with a null
  `feed_url` for CSV rows**: rejected — see decision 1; CSV import has no
  ongoing state to track (no URL, no health, no "last synced," just a
  one-time file processed), so a row would only ever have a `provider` and
  nothing else meaningful, which is a sign it doesn't belong in this table.
- **A new `organization.integrations.manage` capability constant, wired
  through `AuthorizationService`**: rejected — see decision 3; would be the
  only ORGANIZATION-context capability actually enforced anywhere in this
  codebase, inconsistent with how every sibling org-settings feature already
  works.
- **A dedicated `/app/organizations/{id}/integrations` route**: rejected — see
  decision 4; no other org-settings area gets its own route, all live as
  sections on `OrganizationDetailPage`.

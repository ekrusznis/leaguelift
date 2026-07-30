# Architecture Decision Records

Format:

```markdown
# ADR-NNN: Title

## Status
Proposed | Accepted | Superseded

## Context

## Decision

## Consequences

## Alternatives Considered
```

Numbers reserved by `DESIGN-DOC.md` section 22:

- ADR-001: Modular monolith
- ADR-002: Managed OIDC authentication
- ADR-003: PostgreSQL and Flyway
- ADR-004: JDBC first, jOOQ before finance/reporting
- ADR-005: Stripe Connect charge model
- ADR-006: Transactional outbox
- ADR-007: Immutable ledger and credit events
- ADR-008: DigitalOcean deployment
- ADR-009: Adult-controlled household accounts
- ADR-010: Family credits as non-withdrawable fee credits
- ADR-011: Public-page content model
- ADR-012: File storage and upload security

ADR-001 through ADR-004 are written (Accepted) as part of the Phase 0 foundation.
ADR-012 is written (Accepted) as part of Phase 1's file-upload/branding slice
(2026-07-28, organization logo/cover only — see the ADR itself).
ADR-005 is written (Accepted) as part of Phase 2's remainder slice (2026-07-28,
separate-charges-and-transfers; Connect Express onboarding only, no live charge
routing yet — see the ADR itself).
ADR-006 through ADR-011 are proposed later in `DESIGN-DOC.md` (fundraising, commerce,
ledger, and infra phases) and should be written when those milestones begin — they are
intentionally not pre-written here to avoid locking in unresolved product questions
(see `DESIGN-DOC.md` section 19.3).

Additional, unreserved decisions:

- ADR-013: Java 17 instead of Java 21 baseline (environment-driven, see the ADR itself)
- ADR-014: Traditional email/password authentication, superseding ADR-002 (managed
  OIDC/Auth0 was never actually configured against a real tenant)
- ADR-015: Inbound webhook consumption (the `webhook_event` table and a real
  Stripe webhook receiver) pulled forward from Phase 5, scoped narrowly to
  confirming campaign contributions via `checkout.session.completed` — written
  as part of Phase 3's contribution-recording slice (2026-07-29)
- ADR-016: Printify integration scope for Phase 4 slice 1 — vendor selection is
  a US-location filter, not a price comparison (Printify's catalog exposes no
  cost/price); a variant's real cost is learned once via product creation, not
  guessed; fulfillment submission is draft-only (no send-to-production); no
  auto-design/personalization (2026-07-29)
- ADR-017: Phase 5 financial model — 5% flat platform fee (configurable,
  starting value not final), payout transfers gated by a configurable holding
  period (default 7 days) with manual-trigger-only firing (no automatic
  scheduler yet), org-admin-initiated refunds within a 14-day window,
  negative balances deducted from an org's next payout (2026-07-29)
- ADR-018: Sponsorship scope and charge model for Phase 6 slice 1 — reuses the
  existing separate-charges-and-transfers model with no separate sponsorship
  payment account (resolves §19.3 open question #19 for this slice), reuses
  CONTRIBUTION-shaped ledger entries with a new SPONSORSHIP source type
  rather than a new entry type, sponsor logo assignment is an org-admin
  action after confirmation rather than a public self-service upload (the
  media pipeline has no anonymous-upload path today), no refunds this slice
  (2026-07-29)
- ADR-019: Sponsorship approval workflow, refunds, renewal reminders,
  QR/link sharing, invoices, and sponsor-CRM widening (Phase 6 remainder) —
  a new `review_status` column gates public directory visibility separately
  from payment status; rejecting a sponsorship atomically refunds it via the
  same 14-day-window Stripe+ledger refund flow now built for sponsorships;
  a `@Scheduled` renewal-reminder job (deliberately outside the still-
  unconsumed outbox pattern) backed by a new minimal logging-only
  `EmailProvider`; ZXing-generated QR codes with no persistence/tracking;
  computed (not stored/numbered) invoices; `sponsor` widened with
  phone/company_name/notes (2026-07-29)
- ADR-020: Phase 7 capability-based authorization model — additive
  `role_assignment`/`guardian_relationship` tables alongside
  `organization_membership` (not a replacement); a new `AuthorizationService`
  with deny-by-default resource-scoped checks and org owner/admin
  team/tournament inheritance; `TEAM_ADMINISTRATOR`/`TOURNAMENT_ADMINISTRATOR`
  org roles now grant zero implicit team/tournament access (closing the
  "not actually scoped to just Varsity Soccer" gap); a guardian-authorized
  athlete self-link formalizing the existing seeded "controlled test account"
  pattern (not general athlete login); real `platformAdministrator` resolution
  (previously hardcoded false); Coach/Athlete/Tournament/Platform Admin
  dashboards wired to live data; most existing `MembershipService` call sites
  deliberately not migrated this phase (2026-07-29)
- ADR-021: Phase 7 completion pass — nav+widget registries for all six
  dashboards, a real admin UI for team/tournament role-assignment
  grant/revoke, a coach team selector, Documents (migration V19,
  `document_acknowledgment`), a cross-org Activity Feed, Global Search,
  platform-wide Orders/Payments/Payouts on the Platform Admin dashboard, a
  demo-data audit bugfix; an interactive context-switching UI was prototyped
  then explicitly reverted (dashboard routing stays fixed-priority-by-role);
  the full `MembershipService`→`AuthorizationService` migration explicitly
  deferred to a later phase (2026-07-29)
- ADR-022: Phase 8 slice 1 — outbox worker (claim/dispatch/backoff/dead-letter)
  and a real Resend-backed `EmailProvider` (resolving a Resend-vs-SendGrid doc
  inconsistency in Resend's favor); real invitation emails for
  `membership.invited`; sponsorship renewal reminder rebuilt as a scan-then-
  handler outbox consumer; a platform-admin dead-letter/failed inspection +
  reprocess endpoint (2026-07-30)
- ADR-023: Phase 8 slice 2 — notification trigger expansion: fee-payment
  reminders (new scanner/handler + `fee_assignment.payment_reminder_sent_at`),
  order-confirmation and contribution-thank-you emails written directly from
  their webhook-confirmation methods, sponsorship approval/refund notices,
  and a minimal `household.email_reminders_opt_out` flag gating only
  recurring reminders (not one-time transactional confirmations);
  "campaign launch emails" explicitly deferred — no subscriber/mailing-list
  model exists to trigger it from (2026-07-30)
- ADR-024: Phase 8 slice 3 — one-way SMS via Twilio (plain REST API, no
  official SDK), gated on a new `household.sms_reminders_opt_in` opt-IN flag
  (opposite default from email's opt-out); wired only to fee-payment
  reminders (the one recurring reminder that concretely exists) as a second,
  independent channel alongside email; fundraising SMS/email reminders and
  campaign-launch emails remain deferred pending a subscriber-model decision
  (2026-07-30) — completes Phase 8's roadmap scope
- ADR-025: Phase 9 — a new `reporting/` module with date-ranged org reports
  (revenue by source/team, campaigns, product performance, refunds, fee
  collections, plus CSV export); a household fee report scoped to fees/
  payments/balance only (credits/orders/contributions deferred — no credit
  rules and no household attribution FK on orders/contributions exist yet);
  a platform report (new orgs/customers, GTV, refund rate, integration
  health) excluding subscription revenue and dispute rate (no backing
  data); and an `AnalyticsProvider` seam shipped as a logging-only stub, no
  vendor chosen, with one real call site (`organization_created`)
  (2026-07-30)
- ADR-026: Phase 10 slice 1 — event data model (`event`, V22) and staff CRUD
  (create/read/update/publish/cancel/postpone), org/team/tournament-scoped;
  reuses the existing team/tournament capability-inheritance architecture
  unchanged, no new AuthorizationService methods; collapses the doc's
  separate "status"/"publication status" fields into one lifecycle enum;
  records three founder decisions shaping all of Phase 10 (simple v1 RSVP
  policy, aggregate RSVP counts also shown to athletes/guardians, no
  per-org feature toggle) even though RSVP itself is slice 2 (2026-07-30)
- ADR-027: Phase 10 slice 2 — RSVP (`event_rsvp`, V23, upserted per
  event+participant, not append-only); submission requires a real self/
  guardian/staff relationship resolved directly (never through the broader
  `hasHouseholdCapability` check, which would let any org staff member
  impersonate a guardian); staff get individual responses, guardians/
  athletes get aggregate counts only; household/participant combined-
  schedule listing; `AthleteDashboardService`'s next-event/week-schedule
  cards wired to real data, closing a gap flagged since Phase 7 (2026-07-30)
- ADR-028: Phase 10 slice 3 — `CalendarProvider` (real RFC 5545 `.ics`
  output, UTC timestamps, no `VTIMEZONE`) and `MapsProvider`
  (`GoogleMapsDirectionsProvider` — genuinely real, keyless, unlike every
  other provider stopgap in this codebase); both live in the `event` module,
  not `notification/`; neither ever reads `meetingPoint`/`directionsNotes`,
  so private location notes can't leak by construction; reuses existing
  read authorization with no new auth path; `EventController`'s team-name
  resolution consolidated into `EventService.displayTitleFor` (2026-07-30)
- ADR-029: Phase 10 slice 4 — notification wiring into Phase 8's outbox
  worker: `EventService` diffs each mutation's before/after snapshot and
  writes one outbox event per changed dimension (`event.created`,
  `event.time_changed`, `event.location_changed`, `event.area_assigned`,
  `event.arrival_time_changed`, `event.postponed`, `event.cancelled`,
  `tournament.event_added`, `tournament.event_updated`), never for a
  still-DRAFT event; family recipients resolve only through `Event.teamId`'s
  roster (never `opponentTeamId`), respecting the existing email/SMS opt-out/
  opt-in flags; `event.rsvp_changed` notifies team staff instead (a new
  `AuthorizationService.listTeamStaffUserIds`), not the submitting family;
  nine of the ten event types share one `EventChangeNotificationHandler`
  class registered as nine distinct `@Bean` instances rather than nine
  near-identical `@Component` classes; "in-app state" is the pre-existing
  Activity Feed, not a new notification inbox (2026-07-30) — completes
  Phase 10's roadmap scope
- ADR-030: Phase 11 mobile/responsive audit scope — audit-and-fix only,
  the WebView-vs-native app-shell decision and standalone registration
  workflows stay deferred behind the roadmap's pilot-evidence gate; audit
  method is code-level markup/Tailwind review, not live-viewport
  screenshots (`resize_window` doesn't actually change `window.innerWidth`
  in this environment, confirmed via direct JS check); public-facing pages
  and checkout flows audited before authenticated dashboards (2026-07-30)
- ADR-031: Phase 12 slice 1 — Integrations page and connector foundation.
  First org-connected connectors are CSV import and ICS feed subscription,
  not MaxPreps/GameChanger/SportsEngine (no verified vendor account/API
  terms for any of the three); those three appear as disabled "coming
  soon" cards, not omitted or fake-wired. A new `event_source_connection`
  table (V24) represents only stateful connections — CSV import gets no
  row, it's a one-time action (slice 2). Authorization reuses
  `MembershipService.requireManagerRole`, not a new `AuthorizationService`
  capability, since no ORGANIZATION-context action in this codebase routes
  through that service. The page is a new section on the existing
  `OrganizationDetailPage`, not a separate route (2026-07-30)
- ADR-032: Phase 12 slice 2 — CSV import connector. One upload is scoped
  to exactly one team (or org-wide) and one shared timezone, never
  per-row; required columns are `external_id` (caller-supplied stable
  identifier, no fuzzy matching) and `event_type`. `connection_id`
  (populated for the first time) is the target team id or `"org:{id}"`,
  making the existing `(provider, connection_id, external_event_id)`
  index do real dedup; a SHA-256 hash of every imported field
  (`external_sync_hash`) makes a re-upload of an unchanged file a safe
  no-op. Imported events start TENTATIVE, not DRAFT — a deliberate
  departure from manual creation's DRAFT-first default, since a bulk
  import has already been reviewed outside the system. Writes bypass
  `EventService` and Phase 10 slice 4's notification wiring entirely, to
  avoid flooding families with one email per imported row; one audit
  event per upload is recorded instead. Row-level failures are collected,
  never abort the batch. No CSV-parsing library was added — a hand-rolled
  RFC 4180 parser, same call as ICS generation (ADR-028) (2026-07-30)

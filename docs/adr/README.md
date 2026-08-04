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
- ADR-033: Phase 12 slice 3 — ICS feed connector. A new `IcsFeedParser`
  is the read-side counterpart to `IcsCalendarProvider` (ADR-028) —
  UID/SUMMARY/DESCRIPTION/LOCATION/DTSTART/DTEND/STATUS only, everything
  else ignored, no recurrence expansion, no new dependency. Slice 1's
  connect flow didn't anticipate two things the poller needs — timezone
  (resolves floating DTSTART/DTEND) and team scope (mirrors CSV
  import's own per-upload team) — both added now via V25 rather than
  deferred. UID is the `external_event_id`, reusing the same
  `(provider, connection_id, external_event_id)` dedup index and
  SHA-256 `external_sync_hash` change-detection ADR-032 established. A
  synced STATUS can set a *new* event's initial status but never
  overwrites an existing event's status on update, so an external feed
  can't silently undo a staff postpone/cancel. One connection's failure
  never blocks another's; one event's failure never blocks the rest of
  that feed — a plain `@Scheduled` job (default every 30 minutes),
  mirroring `FeePaymentReminderScanner`'s shape (2026-07-30)
- ADR-034: Phase 12 slice 4 — source-owned vs. overlay field enforcement,
  completing Phase 12's roadmap scope. `EventService.update()` rejects
  any attempt to set a source-owned field (status/start/end time/venue/
  opponent) on an imported event — a presence-based check matching
  `update()`'s own null-means-unchanged convention, not a diff against
  current values. Only "detach from source" (of section 14.1A's three
  named options) was built — a new `detachFromSource` clears every
  import-identity column, converting the event back to MANUAL;
  "temporary local override" would need a whole second per-field
  override data model section 14.1A frames as one option, not a
  requirement, so it stays unbuilt pending real usage evidence.
  Overlay fields (title/description/arrival/meeting point/directions
  notes/area/visibility) stay freely editable — `area` is grouped as
  overlay, not source-owned, since `event.area_assigned`'s own
  existence as a distinct notification type already implies routine
  local assignment (2026-07-30) — completes Phase 12's roadmap scope
- ADR-035: Phase 13 Production Readiness Review scope — splits into four
  code-level slices buildable this session (security headers/secrets-
  audit-finalization/404-vs-403 review, test-coverage gap review against
  section 18.1's critical-scenarios list, accessibility code-level audit
  mirroring ADR-030's mobile-audit methodology, and operational runbook
  documentation) versus items explicitly flagged as needing real
  infrastructure or stakeholders this session doesn't have (load testing,
  backup-restore rehearsal, incident-response rehearsal, UAT, legal
  sign-off, the founder's own go/no-go decision) — never fabricated or
  simulated (2026-07-30)
- ADR-036: Phase 13 slice 1 — security hardening. Added the six missing
  HTTP security headers to `SecurityConfig` (CSP/HSTS/X-Content-Type-Options/
  X-Frame-Options/Referrer-Policy/Permissions-Policy); manually verified
  Swagger UI still renders and executes under the new CSP (browser
  screenshot + console check, no violations). Finalized the secrets audit
  Phase 8 deferred: `.env.example` was missing 14 real, non-secret config
  variables (platform economics, sponsorship/fee reminder scanners, outbox
  worker tunables) — added; no plaintext secrets found anywhere in the repo.
  404-vs-403 review of all 18 `ForbiddenException` sites confirmed the
  codebase already returns 404 for cross-organization resource lookups and a
  uniform 403 for organization-membership denial regardless of whether the
  org exists — no leak found, now written down as a checked invariant
  (2026-07-30)
- ADR-037: Phase 13 slice 2 — test-coverage gap review against section
  18.1's 21 named critical scenarios: 13 already solidly covered, one
  (audit-on-mutation) stubbed but never actually verified across five test
  bodies — fixed with real `verify()` assertions; five real gaps against
  already-correct existing behavior (athlete-cannot-view-sibling's-schedule,
  tournament TBD-field identity preservation, fulfillment-failure-doesn't-
  erase-a-paid-order, public store field/status exposure, archived-page
  public-read blocking) closed with new tests; a first-ever real-HTTP
  security test (`SecurityConfigIntegrationTest`, plain JDK `HttpClient`
  since Spring Boot 4 dropped `TestRestTemplate` from `spring-boot-test`)
  proves no auth bypass and that an unauthenticated visitor is correctly
  rejected; two reflection-based checks (`MoneyArithmeticTest`) guard money
  fields staying `Long`/never floating-point and `LedgerEntryRepository`
  staying append-only. Two scenarios left as documented gaps, not silently
  dropped: partial refunds are a missing *feature* (not a missing test,
  needs a founder decision on the split formula) and prior-release
  migration testing needs release-snapshot tooling this repo doesn't have
  (2026-07-30)
- ADR-038: Phase 13 slice 3 — accessibility code-level audit, same
  methodology ADR-030 used for the mobile audit (static source review, not
  live-viewport testing). No ESLint config exists anywhere in this repo, so
  this is the first accessibility review this codebase has ever had. Fixed:
  missing `aria-describedby` linking a field to its own error message,
  systemic across 16 hand-rolled forms (the shared `FormField` primitive
  already did this correctly — the gap was files not using it); a broken
  label association and an unlabeled share-link input; low-contrast
  `text-slate-500`-on-`navy-800` text on two auth pages (computed ~3.87:1,
  below WCAG AA); an `h1`→`h3` heading-hierarchy skip on every dashboard,
  root-caused and fixed once in `DashCard`; `GlobalSearchBox`'s results
  dropdown was mouse-only — added the full ARIA combobox pattern
  (`role="combobox"`/`"listbox"`/`"option"`, `aria-activedescendant`,
  arrow-key/Enter/Escape handling), verified live against real seeded data
  in a real browser. Left as a documented gap, not built: the entire
  dashboard sidebar navigation is non-interactive (every nav item is a
  plain `<span>`, root-caused in `navRegistry.tsx`'s missing route field) —
  the same gap ADR-030 already found and declined to fix as a feature build
  rather than an accessibility bug fix (2026-07-30)
- ADR-039: Phase 13 slice 4 — operational runbook documentation. Rewrote
  section 18.3 from four generic paragraphs into seven procedures naming
  real, currently-existing endpoints/tables/config keys: 5xx and migration
  failures expanded with real correlation mechanics; a security-incident
  procedure covering per-secret rotation for every real secret this
  codebase has; a new financial/ledger-incident procedure (the prior text
  explicitly deferred this "until Phase 5," which has been live since
  2026-07-29) documenting the append-only ledger's diagnostic value and the
  real reversing-entry correction path; a new outbox-backlog/webhook-failure
  procedure pointing at the real `/api/v1/admin/outbox-events/*` recovery
  endpoints, honestly noting section 18.2's backlog metric is still
  unimplemented; and a real four-step backup/restore target procedure once
  DigitalOcean Managed PostgreSQL is provisioned. Every procedure explicitly
  labeled written-but-not-yet-rehearsed, per ADR-035's discipline against
  fabricating live-infrastructure results — completes Phase 13's full
  four-slice scope (2026-07-30)
- ADR-040: Phase 14 prerequisite dashboard connectivity and routed navigation (2026-07-31)
- ADR-043: Phase 14 Platform Admin support console and reasoned support access (2026-07-31)
- ADR-045: Phase 16 slice 3 branding and private profile photos (2026-07-31)
- ADR-046: Phase 16 slice 4 typed profile-correction requests (2026-07-31)
- ADR-047: Phase 16 slice 5 reusable event templates (2026-07-31)
- ADR-048: Phase 16 slice 6 preview-confirmed season rollover (2026-08-01)
- ADR-049: Phase 17 Help Center, support intake, and shared footer foundation (2026-08-01)
- ADR-050: Phase 17 Action Center, announcements, and reminder delivery (2026-08-01)
- ADR-051: Phase 18 slices 1-2 manual catalog/vendor records and controlled fulfillment, tracking, exception, and reprint operations (2026-08-01)
- ADR-052: Phase 18 slice 3 explicit offline contribution, sponsorship, and manual-order records with pending verification, idempotency/duplicate protection, balanced non-payout ledger settlement, Action Center review work, acknowledgement delivery, and the cross-phase Help Center coverage standard (2026-08-01)
- ADR-053: Phase 18 payment plans, controlled financial corrections, and durable reconciliation (2026-08-01)
- ADR-054: Phase 19 generalized provider catalog, encrypted owner-scoped connection/OAuth foundation, deterministic stubs, and fail-closed runtime guards (2026-08-01)
- ADR-055: Phase 19 personal/organization/platform integration placement and disabled Google Calendar scaffold with ICS fallback (2026-08-02)
- ADR-056: Phase 19 QuickBooks/sports-data provider scaffolds, durable sync/issue history, platform-provider contract hardening, and disabled-by-default completion (2026-08-02)
- ADR-057: Product renamed from LeagueLift to Rally26 (leaguelift.io -> rally26.com), full codebase/package/domain sweep (2026-08-03)

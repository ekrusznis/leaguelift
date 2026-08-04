# ADR-022: Phase 8 Slice 1 — Outbox Worker and Real EmailProvider

## Status
Accepted

## Context

DESIGN-DOC.md section 17 has reserved the outbox pattern since Phase 0:
`outbox_event` rows are written by `InvitationService` (`membership.invited`),
`MediaUploadService` (`media.asset.ready`), and `MediaAssignmentService`
(`media.assignment.published`), but nothing has ever consumed them — no
claim/dispatch/retry/backoff/dead-letter logic exists. Phase 6 remainder
(ADR-019) built a `notification/EmailProvider` interface and a logging-only
implementation as a deliberate stopgap for sponsorship renewal reminders,
explicit in both its own doc comments and ADR-019 that the real send path and
the real outbox consumer are Phase 8 work. Phase 1's admin-invitation flow has
the same gap in the other direction: `membership.invited` is written and
already carries everything an email would need, but no email has ever
actually been sent — invitations are token-link only.

This ADR covers Phase 8 slice 1 only: the outbox worker itself and a real
`EmailProvider`. It deliberately does not cover SMS/Twilio (a later slice —
one-way SMS needs its own consent-tracking decision) or the broader set of
notification trigger points DESIGN-DOC.md section 13's Communication catalog
lists (fee-due reminders, campaign-launch emails, order/contribution
confirmations) — those are Phase 8 slice 2, built on top of the consumer this
slice provides.

Two decisions were confirmed directly with the founder before implementation,
per the "do not guess, but don't build unnecessary frameworks around an
unresolved question" instruction in DESIGN-DOC.md section 19.3:

## Decision

**1. Real `EmailProvider` implementation targets Resend, not SendGrid.**
DESIGN-DOC.md's Phase 8 roadmap row (section 14.1) says "e.g. SendGrid," but
`.env.example`'s `RESEND_API_KEY` placeholder, `EmailProvider.kt`'s own doc
comment, and ADR-019 all already point at Resend — that inconsistency is
resolved in Resend's favor since no SendGrid placeholder or reference exists
anywhere else in the codebase. `notification/infra/ResendEmailProvider.kt`
implements the existing `EmailProvider` interface unchanged (`send(EmailMessage)`)
using a plain `RestClient` against `https://api.resend.com/emails`, following
the `PrintifyConfig` pattern (no official Kotlin/Java Resend SDK is assumed;
a plain authenticated REST client mirrors how Printify is already wrapped,
rather than pulling in an unofficial SDK dependency for one endpoint). A new
`ResendProperties` (`rally26.email.resend`, `api-key` + `from-address`)
departs from `PrintifyProperties`/`StripeProperties`'s "no default in
staging/prod" convention on purpose: both fields keep a blank Kotlin default
in **every** profile, because unlike Stripe/Printify (always active, no
on/off switch), Resend is only consulted when a new
`rally26.email.provider` property equals `"resend"` — `"logging"` (the
default everywhere) is a fully legitimate mode where these values are simply
unused, so there's no single env var whose absence should fail startup. The
safety net for a real deployment that flips the switch without a real key is
the same one Printify already relies on: a blank key still produces a
working client, and a resulting 401 is translated into a clean
`ServiceUnavailableException` at call time, not a build/startup failure.
`LoggingEmailProvider` (`@ConditionalOnProperty(..., havingValue = "logging",
matchIfMissing = true)`) remains active in every environment without
`rally26.email.provider = resend` — which is every environment today,
since no real Resend credentials are configured anywhere yet, same posture
as Stripe/Printify.

**2. The outbox worker is a single scheduled poller with row-level claiming,
not a message-broker consumer.** A new `outbox/application/OutboxWorker.kt`
runs on a configurable fixed delay (`rally26.outbox.worker.poll-interval-ms`,
default 5000), claiming a batch of due `PENDING`/`FAILED` rows (`available_at
<= now()`) via a single `with ... for update skip locked` CTE update
(Postgres row-level locking — safe for a single-instance deployment today and
forward-compatible with multiple instances later without code changes),
transitioning each to `PROCESSING` and incrementing `attempt_count` in that
same statement. `FAILED` (not just `PENDING`) is claimable because a
retryable failure moves a row to `FAILED` rather than back to `PENDING` —
see below; both existing `outbox_event.status` values the check constraint
already reserved. Dispatch is by `event_type` string to a registered
`OutboxEventHandler` bean (`interface OutboxEventHandler { val eventType:
String; fun handle(event: OutboxEvent) }` — Spring collects every
implementing bean into a `List<OutboxEventHandler>`, the worker builds a
`Map<String, OutboxEventHandler>` from it at startup). A handler with no
matching `event_type` is left claimed-but-untouched (`PROCESSING`) rather
than silently dropped or auto-marked `PROCESSED` — an unhandled event type is
a bug to notice, not a no-op. On handler success the row moves to
`PROCESSED` with `processed_at` set; on failure, `last_error` records the
exception message and, if `attempt_count` is still below
`rally26.outbox.worker.max-attempts` (default 5), the row moves to
`FAILED` with `available_at` pushed forward by an exponential backoff
(`rally26.outbox.worker.backoff-base-seconds`, default 30, doubled per
attempt, capped at `backoff-cap-seconds`, default 3600) — otherwise it moves
to `DEAD_LETTER` instead of retrying further. Using `FAILED` (rather than
just resetting to `PENDING`) for the backoff-wait state is deliberate: it's
what makes `PlatformAdminDashboardService.getOutboxHealth`'s existing,
previously-always-zero `failed` count (distinct from `pending`/`deadLetter`)
finally mean something — "actively erroring and backing off" versus "never
yet attempted." `PROCESSING` rows never revert automatically if the process
crashes mid-batch — see Consequences. Not wrapped in `@Transactional`:
dispatch happens via a method reference from within the same bean
(self-invocation), which Spring's proxy-based AOP silently ignores anyway,
and each repository call is already its own atomic single statement.

**3. `membership.invited` gets its first real consumer: an actual invitation
email.** `InvitationEmailHandler` (in `invitation/`, implementing
`OutboxEventHandler` for `event_type = "membership.invited"`) parses the
existing payload (`invitationId`/`email`/`role`), looks the invitation back up
by ID (per the existing code comment in `InvitationService` — never trusting
a token that traveled through the event payload), and sends an email with the
invitation's accept link via `EmailProvider`. This closes the "admin
invitations are token-link only — no email delivery" gap the roadmap has
called out since Phase 1.

**4. `SponsorshipRenewalReminderService` is rebuilt as an outbox-event
handler, per ADR-019's own forward note — but `markRenewalReminderSent` moves
to the *scanner*, not the handler, correcting a bug in this ADR's original
plan.** The `@Scheduled` cron job is replaced by a `SponsorshipRenewalScanner`
(`@Scheduled`, same cron/properties as before) that finds due candidates and,
for each, writes one `sponsorship.renewal_reminder_due` outbox event (payload
includes sponsor name/contact-email/package name/placement-end-date — all
already visible to the org admin who approved the sponsorship, so no
re-lookup-by-ID is needed the way the invitation token deliberately requires
one) and immediately calls `markRenewalReminderSent` right after enqueueing,
in that order. `SponsorshipRenewalReminderHandler` (`OutboxEventHandler` for
that event type) only sends. Marking reminded in the handler instead (the
original plan) would have reopened exactly the bug the outbox is supposed to
prevent: a sponsorship stuck retrying through backoff (e.g. Resend down for a
day) still has `renewal_reminder_sent_at IS NULL` until the handler
eventually succeeds, so the next day's scan would enqueue a *second* event
for the same sponsorship. Marking it in the scanner makes the scan itself
idempotent — each sponsorship is enqueued exactly once — at the cost of a
small, accepted risk in the other direction: a crash between the outbox
insert and the mark-reminded update could leave a sponsorship re-eligible for
tomorrow's scan, i.e. a possible duplicate reminder, never a silently skipped
one. Writing the event *before* marking reminded (rather than the reverse)
is what keeps that risk one-sided.

**5. Admin visibility extends the existing outbox counts via a new
`outbox/web/OutboxAdminController.kt`, not a new module.**
`PlatformAdminDashboardService` already exposes `countByStatus` aggregates
(DESIGN-DOC.md section 10.2/18.2). This slice adds `GET
/api/v1/admin/outbox-events/dead-letter` and `.../failed` (both gated on the
existing `PLATFORM_INTEGRATION_VIEW` capability) plus `POST
/api/v1/admin/outbox-events/{id}/reprocess` (gated on a new
`PLATFORM_INTEGRATION_MANAGE` capability — deliberately separate from
`_VIEW`, since this one mutates state: resets a `DEAD_LETTER`/`FAILED` row to
`PENDING` with `attempt_count = 0` and clears `last_error`) so a stuck event
is inspectable and recoverable without a direct database session —
satisfying section 17's "provide admin visibility" and "allow controlled
reprocessing" requirements for the first time. `PLATFORM_INTEGRATION_MANAGE`
is added to both `Capabilities.kt` and the mirrored frontend
`capabilityConstants.ts`, granted to `PLATFORM_ADMINISTRATOR` alongside
`_VIEW` in `CapabilityRegistry.platformCapabilities`.

## Consequences

- The outbox worker is real, general-purpose infrastructure now — every
  future notification trigger (Phase 8 slice 2's fee reminders,
  campaign-launch emails, order confirmations, etc.) is "write an outbox
  event + add an `OutboxEventHandler` bean," not new per-feature scheduling
  or retry logic.
- A crash between claiming a row (`PENDING` -> `PROCESSING`) and finishing
  its handler leaves that row stuck in `PROCESSING` forever — no
  reaper/stale-claim-recovery job exists yet. Acceptable for this slice
  (single-instance deployment, `for update skip locked` avoids double-
  processing across ticks) but a future multi-instance or long-running-crash
  scenario would need one; the platform-admin reprocess endpoint (decision 5)
  covers manual recovery in the meantime, including for stuck `PROCESSING`
  rows if the admin API is widened to allow that status too — not built in
  this slice, `DEAD_LETTER`/`FAILED` only.
- `LoggingEmailProvider` stays the active bean everywhere
  `rally26.email.provider` isn't explicitly set to `resend` (which is
  everywhere today) — the only observable behavior locally/in staging is
  still a log line until both the provider is flipped and a real
  `RESEND_API_KEY`/`RESEND_FROM_ADDRESS` are supplied. This is the same "real
  code, no live credentials yet" posture Stripe/Printify already have, not a
  regression from ADR-019.
- `media.asset.ready`/`media.assignment.published` remain unconsumed after
  this slice — no `OutboxEventHandler` is registered for them, so they sit
  `PENDING` indefinitely under decision 2's "no silent auto-processed" rule.
  This is expected: neither event has an obvious email recipient today (an
  asset/assignment doesn't identify who should be notified), and inventing
  one would be scope creep this slice doesn't need. A future slice should
  either give them a real handler or stop writing them if they're never
  going to have one.
- `SponsorshipRenewalReminderService`'s existing test coverage
  (query-and-email-in-one-method) needs restructuring into scanner tests
  (event written for each due candidate) and handler tests (email sent,
  `markRenewalReminderSent` called) — behavior is preserved, but the single
  method it lived in is now two.
- This slice does not touch SMS, notification preferences/opt-out, or any
  new trigger points beyond `membership.invited` and the sponsorship
  renewal reminder — those remain slice 2 (broader trigger coverage) and
  slice 3 (Twilio SMS, gated on a new consent column) per the Phase 8
  roadmap entry.
- A repo-wide secrets audit done alongside this ADR (2026-07-30) found no
  hardcoded/plaintext secrets anywhere in tracked source — every existing
  credential-bearing config (`JwtProperties.secret`, `SpacesProperties`'s
  access/secret keys, `StripeProperties`, `PrintifyProperties.apiToken`)
  already follows the "blank locally, no default in staging/prod, fail
  startup rather than run unauthenticated" pattern this ADR's new
  `ResendProperties` (and the later `TwilioProperties`) must follow too.
  `.env`/`.env.local` are gitignored and confirmed untracked; CI already
  uses `${{ secrets.* }}` correctly (`security.yml`). Actually provisioning
  real staging/prod values as encrypted GitHub Environment secrets and
  wiring them into a real deploy pipeline stays out of scope here —
  `deploy.yml` is still a placeholder pending ADR-008 (DigitalOcean
  deployment), and that wiring belongs with whichever phase first stands up
  a real deployed environment (Phase 12's launch-gate secrets/hardening
  review, at the latest) — not invented ahead of that need in this slice.

## Alternatives Considered

- **A message broker (e.g. RabbitMQ/SQS) instead of a polling worker over
  `outbox_event`**: rejected — DESIGN-DOC.md section 17 already committed to
  the transactional-outbox pattern specifically so a service can write an
  event in the same transaction as its own state change with no distributed
  transaction or broker dependency; introducing a broker now would be new
  infrastructure this pilot-stage codebase doesn't need (section 18.4's
  "don't optimize for scale the pilot hasn't reached yet").
- **SendGrid instead of Resend**: rejected — see decision 1; every existing
  reference in the codebase already points at Resend, and there was no
  standing reason to introduce a second provider placeholder just to match
  the roadmap table's example wording literally.
- **A real Resend SDK dependency instead of a plain `RestClient`**: rejected
  for consistency with `PrintifyConfig`'s existing no-SDK precedent — a
  single-endpoint plain REST client avoids a new dependency for one HTTP
  call, and keeps the "wrap the provider's HTTP API directly" pattern
  uniform across `integration/printify` and `notification/infra`.
- **A stale-claim reaper job in this slice**: rejected as premature — no
  multi-instance deployment exists yet and a single-instance crash mid-batch
  is a rare, manually-recoverable event today (decision 5's reprocess
  endpoint, once widened, covers it); revisit if/when a real production
  incident or a multi-instance deployment plan makes it necessary.
- **Auto-marking an unrecognized `event_type` as `PROCESSED` (a no-op ack)**:
  rejected — silently dropping an event nobody wrote a handler for hides a
  real gap (a forgotten handler, or a payload nobody uses) rather than
  surfacing it; leaving it `PENDING` keeps it visible in the platform-admin
  backlog count instead.
- **Building SMS/Twilio in this same slice since Phase 8's roadmap line
  groups all three together**: rejected — SMS needs its own consent-tracking
  decision (no `sms_opt_in` column exists yet) and is independently
  testable/shippable; bundling it here would make this slice larger without
  making the outbox worker or `EmailProvider` any more correct.

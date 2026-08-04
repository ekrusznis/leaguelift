# ADR-019: Sponsorship Approval Workflow, Refunds, Renewal Reminders, QR/Link Sharing, Invoices, and Sponsor CRM Widening (Phase 6 Remainder)

## Status
Accepted

## Context

Phase 6 slice 1 (V16, ADR-018, 2026-07-29) shipped the narrowest real
sponsorship vertical slice: publish a fixed-price package, purchase it via
test-mode Stripe Checkout, confirm it via the existing webhook receiver, and
show it on a public sponsor directory immediately on confirmation. ADR-018
explicitly deferred approval workflow, renewal reminders, invoices, link/QR
tracking, refunds, and sponsor-contact CRM beyond name/email to a later slice
— this is that slice, closing most of the remaining gap to DESIGN-DOC.md
section 13's full Sponsorships catalog (exclusivity beyond max-quantity-of-1
was already built in slice 1 and is intentionally untouched here; reporting
stays out of scope).

Five product decisions were made directly by the founder before this slice
was implemented (2026-07-29), matching the "do not guess, but don't build
unnecessary frameworks around an unresolved question" instruction in
DESIGN-DOC.md section 19.3:

## Decision

**1. Approval workflow: a new `review_status` column, separate from the
existing `status` payment-lifecycle column.** A confirmed sponsorship no
longer appears on the public sponsor directory automatically. `Sponsorship`
gains `SponsorshipReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }`
(V17), defaulting to `PENDING_REVIEW`, set independently of `SponsorshipStatus
{ PENDING, CONFIRMED, REFUNDED }`. The two columns are deliberately not
folded into one enum: payment status and review status evolve on different
triggers (Stripe confirms payment; an org admin decides review) and a
sponsorship can legitimately be `CONFIRMED` + `PENDING_REVIEW` at the same
time, or `CONFIRMED` + `REJECTED` simultaneously with `REFUNDED` (see
decision 1b). `SponsorshipRepository.findConfirmedForOrganization` — the
public directory's data source — now filters on both `status = 'CONFIRMED'
AND review_status = 'APPROVED'`.

**1b. Rejecting a sponsorship atomically refunds it — never charged-but-hidden.**
`SponsorshipService.reject` and the refund flow (decision 2) share one
`@Transactional` method (`performRefund`): reject sets `review_status =
'REJECTED'`, records an audit event, then calls the same Stripe refund + ledger
reversal a general refund uses. If the Stripe call fails, the whole
transaction — including the review-status change — rolls back, so a rejected
sponsorship is never left charged. Both `approve` and `reject` require the
sponsorship to be `CONFIRMED` and still `PENDING_REVIEW`; a sponsorship can
only be reviewed once (no re-approving a rejected one, no re-rejecting an
approved one) — a later "undo" workflow would need a separate decision. `reject`
reuses `SPONSORSHIP_REFUND_WINDOW` (decision 2's 14-day window) rather than
bypassing it — in practice a review happens promptly after confirmation
(that's the entire point of gating visibility on it), so the window should
rarely bind, but keeping one code path for "money goes back" is simpler and
safer than a second, unwindowed refund path with subtly different rules.

**2. Refunds: org-admin-initiated, 14-day window from `confirmed_at`, mirroring
ADR-017's contribution/order refund exactly.** `SponsorshipService.refund`
(manager-role, via `MembershipService.requireManagerRole`) is independent of
the review workflow — a sponsorship can be refunded whether or not it was
ever approved. It calls `StripeSponsorshipCheckoutClient.createRefund`
(`reverseTransfer = false`, same rationale as `StripeCheckoutClient.createRefund`)
then `LedgerService.recordRefund(..., LedgerSourceType.SPONSORSHIP, ...)` —
no ledger changes were needed; `recordRefund` already accepted a
`sourceType` parameter generically. `sponsorship` gains `refunded_at`
(V17), completing the forward-compatibility ADR-018 already set up by
allowing `REFUNDED` as a status value with no way to reach it.

**3. Renewal reminders: a `@Scheduled` job, deliberately outside the outbox
pattern, backed by a new minimal `EmailProvider` interface with a
logging-only implementation.** DESIGN-DOC.md section 17: the outbox worker
remains unconsumed (`outbox_event` rows are written but nothing processes
them — Phase 8). Building the reminder feature on infrastructure that
doesn't exist would block a real Phase 6 need on an unrelated future phase.
`SponsorshipRenewalReminderService.sendDueReminders` runs on a configurable
cron (`rally26.sponsorship.renewal-reminder.cron`, default daily at
08:00) and finds confirmed+approved sponsorships whose package
`placement_end_date` falls within a configurable window
(`rally26.sponsorship.renewal-reminder.days-before`, default 14 —
`SponsorshipRenewalReminderProperties`) that haven't already been reminded
(`sponsorship.renewal_reminder_sent_at`, V17, guards against re-sending on
every cron tick). No `EmailProvider` implementation existed anywhere in this
codebase before this slice — `notification/EmailProvider.kt` defines the
interface (the seam DESIGN-DOC.md section 17 already reserved), and
`notification/LoggingEmailProvider.kt` is the only implementation: it logs
what would be sent. This mirrors how `.env.example`'s `RESEND_API_KEY` has
sat unwired since before this slice — no real email-provider credentials
exist in this environment. **This is a deliberate stopgap.** Once Phase 8
builds the real outbox-consumer/notification infrastructure and a real
`EmailProvider` (e.g. Resend-backed), this job should be rebuilt as an
outbox-event handler instead of a direct scheduled query — the
find-candidates/send/mark-sent shape here is exactly what that handler would
do. The reminder emails the sponsor's own contact email (if one is on file);
a sponsor with no contact email is silently marked reminded (nothing to
send) rather than blocking the batch.

**4. QR/link sharing: generated on demand, nothing persisted, no
click-through tracking.** DESIGN-DOC.md section 8.3 listed `qr_code_reference`
as design-target-only; this slice is the first real QR implementation in the
codebase. `build.gradle.kts` adds `com.google.zxing:core`/`:javase` (a small,
well-established library, per the founder's guidance) — `QrCodeGenerator`
wraps ZXing's `MultiFormatWriter`/`MatrixToImageWriter` and returns a
`data:image/png;base64,...` URI directly, so the frontend can drop it into an
`<img src>` with no extra fetch/blob plumbing and no separate authenticated
image-serving endpoint. `GET /organizations/{id}/sponsorship-packages/qr-code?url=...`
is deliberately stateless and org-scoped-but-not-manager-only (any active
member can generate a share link — it carries no sensitive data, the URL is
already public). The frontend constructs the plain URL itself
(`${origin}/sponsors/${organizationSlug}`, the existing public sponsorship
page route) rather than the backend owning a "public site base URL" config —
the frontend already knows its own deployed origin, and this avoids a new
config value that could silently drift from the real deployed frontend
domain across environments. No tracking table, no click analytics — the
founder's decision was explicit on this point ("no click-through analytics,
no tracking table").

**5. Invoices: a computed receipt, not a stored/numbered document.**
`SponsorshipService.getInvoice` assembles `SponsorshipInvoice` (sponsorship +
sponsor + package + organization, all already-persisted rows) on read — no
`invoice` table, no invoice-numbering sequence, no PDF generation library.
`GET /organizations/{id}/sponsorships/{sponsorshipId}/invoice` is readable by
any active org member (`requireActiveMembership`, matching `listConfirmed`'s
own bar, not manager-only) and returns amount/currency/date/sponsor/
package/organization; the frontend renders it as a simple `<dl>` summary, no
PDF export. Available for any sponsorship that reached `CONFIRMED` (including
one since `REFUNDED` — the receipt is a historical record, refunding doesn't
erase that the purchase happened).

**6. Sponsor-contact CRM: three additional columns, not a `sponsor_contact`
table.** `sponsor` gains `phone`, `company_name`, `notes` (V17) —
`SponsorRepository.update`/`SponsorshipPackageService.updateSponsor` follow
the exact coalesce-update pattern `SponsorshipPackageRepository.update`
already established. This remains a single sponsor record per sponsorship's
purchaser, not a multi-contact-per-sponsor-organization CRM — DESIGN-DOC.md
section 13 lists "sponsor contacts" as catalog scope, but a full CRM entity
is more than this proof-of-concept slice needs; the founder's guidance was
explicit about staying bounded here.

## Consequences

- `sponsorship`'s public visibility now requires two things to both be true
  (`status = CONFIRMED`, `review_status = APPROVED`) instead of one — every
  caller of the directory-listing repository method already goes through
  `SponsorshipService.listPublicDirectory`, so this is a single choke point,
  not a scattered check.
- A pre-existing integration test (`SponsorshipIntegrationTest`) asserted
  that a freshly confirmed sponsorship appeared on the directory immediately
  — that assertion changed to reflect the new gate (confirm, then assert
  absence, then approve, then assert presence), rather than being deleted;
  the old behavior was Phase 6 slice 1's explicit interim state, not a
  frozen contract.
- Rejecting a sponsorship is the first place in this codebase where an
  approval decision and a payment reversal are wired together atomically in
  one transaction — a pattern later approval-gated financial flows (if any)
  should follow rather than re-deriving.
- The renewal-reminder job and `EmailProvider` are real, callable code, but
  the only concrete email behavior anyone will observe locally or in
  staging today is a log line — there is still no live email sending
  anywhere in this codebase. Do not treat this ADR as Phase 8 having
  started.
- QR/link sharing adds `com.google.zxing` as a new backend dependency — the
  first image-generation library in this codebase. It's used exactly once
  (`QrCodeGenerator`), with no broader image-processing ambitions.
- Invoices, refunds, and the approval queue are all read/write through
  `SponsorshipService`/`SponsorshipPackageService` — no new module was
  created; `sponsorship/` remains a single cohesive module per DESIGN-DOC.md
  section 6's "only create a module folder when its milestone begins" rule.
  `notification/` is the one new top-level module folder this slice adds,
  because `EmailProvider` is genuinely cross-cutting infrastructure, not
  sponsorship-specific.
- Still not built after this slice: full sponsor-contact CRM (multiple
  contacts per sponsor organization), click-through/QR analytics, invoice
  PDF export or numbering, automatic (non-manual) renewal follow-up beyond
  the one reminder email, and dispute/chargeback handling for sponsorship
  refunds (same gap ADR-017 already left open for contributions/orders).

## Alternatives Considered

- **Folding review status into `SponsorshipStatus` as new enum values (e.g.
  `CONFIRMED_PENDING_REVIEW`, `APPROVED`, `REJECTED`) instead of a separate
  column**: rejected — the founder's own framing explicitly allowed either
  approach but asked for payment-status and review-status to stay
  conceptually separable in the code even if stored as one column; a
  genuinely separate column makes that separation structural rather than a
  documentation convention, and avoids awkward compound states like
  `REFUNDED_AND_REJECTED` that a single enum would eventually need.
- **Platform-admin-mediated approval instead of org-admin**: rejected —
  consistent with ADR-017's refund-authorization precedent (org admins are
  already trusted operators for financial actions on their own organization;
  requiring Rally26 staff involvement would add operational overhead this
  proof-of-concept doesn't need).
- **Leaving reject() as charged-but-hidden with a manual follow-up refund
  step**: rejected — the founder was explicit that this was not acceptable;
  a rejected sponsor should not discover months later that they were charged
  for something never displayed.
- **Building the real outbox worker now to host renewal reminders properly**:
  rejected — that's a substantial, unrelated Phase 8 infrastructure project;
  pulling it forward to support one reminder email would be exactly the kind
  of scope-broadening DESIGN-DOC.md section 20 prohibits ("never silently
  broaden scope").
- **A real Resend-backed `EmailProvider` implementation now, since
  `RESEND_API_KEY` already exists as a placeholder**: rejected — no
  credentials are configured in this environment, and building real HTTP
  integration/tests for a provider this slice doesn't strictly need would be
  scope creep the founder's budget-conscious framing explicitly warned
  against; logging is an accepted stand-in per the founder's own guidance.
- **A `sponsorship_share_link` or `qr_scan_event` tracking table**: rejected
  — the founder was explicit ("no click-through analytics, no tracking
  table"); QR generation is a stateless convenience, not an analytics
  feature.
- **A dedicated `invoice` table with a numbering sequence**: rejected — the
  founder asked for "a downloadable/viewable receipt-style summary," not a
  formal accounting-invoice system; computing it from existing rows avoids
  an entire new consistency surface (numbering gaps, voiding, re-issuing) a
  proof-of-concept doesn't need.
- **A full `sponsor_contact` multi-contact-per-sponsor table**: rejected —
  DESIGN-DOC.md section 13's catalog language ("sponsor contacts") could be
  read this way, but the founder explicitly scoped this down to a bounded
  field-set widening of the existing `sponsor` row.

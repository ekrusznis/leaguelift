# ADR-018: Sponsorship Scope and Charge Model (Phase 6 Slice 1)

## Status
Accepted

## Context

Phase 6 ("Sponsorships and automation," DESIGN-DOC.md §14.1) is the next
unstarted roadmap phase after Phase 5 slice 1 (ledger/real transfers/refunds,
shipped 2026-07-29). Following the same pattern Phase 3 (fundraising) and
Phase 4 (apparel) each used — the narrowest real end-to-end vertical slice,
not the full §13 Sponsorships catalog (approval workflows, renewal reminders,
invoices, link/QR tracking, sponsor-contact CRM) — this slice needed three
concrete decisions before implementation: how a sponsorship purchase is
charged and paid out, how it's represented in the ledger, and how a sponsor's
logo gets attached given the existing media pipeline's upload endpoints are
always manager-role-authenticated.

## Decision

**Sponsorship checkout reuses the existing separate-charges-and-transfers
model (ADR-005/ADR-017) — no separate payment account.** Rally26 charges
the sponsor directly via test-mode Stripe Checkout Session (same as
contributions/orders), and payout to the organization happens through the
existing manual-trigger `PayoutAccountService.triggerTransfer` once the
resulting `ORGANIZATION_EARNING` credit clears its holding period. This
resolves §19.3 open question #19 ("does sponsorship revenue need its own
payment account?") for this slice: no, it shares the org's single Connect
account and the single ledger-based transfer mechanism every other revenue
type already uses. A sponsorship-specific payment account remains available
as a later option if a real pilot sponsor ever needs separately-timed or
separately-routed payouts, but nothing about this slice assumes that need.

**Ledger accounting reuses `LedgerEntryType.CONTRIBUTION`-shaped entries, with
a new `LedgerSourceType.SPONSORSHIP` for traceability — no new entry type.**
`LedgerService.recordConfirmedSponsorship` writes the same three-entry
gross-credit / platform-fee-debit / organization-earning-credit shape
`recordConfirmedContribution` does, because a sponsorship purchase is,
accounting-wise, identical to a contribution (a single gross payment, no
separate production cost the way an order has). A confirmed sponsorship's
ledger rows are still fully traceable back to it via `source_type =
'SPONSORSHIP'`/`source_id`, without proliferating entry types for a category
that behaves identically to an existing one — consistent with how ADR-017's
own entry-type list is deliberately not exhaustive on day one.

**Sponsor logo assignment is an org-admin action performed after a
sponsorship confirms, not a self-service upload by the anonymous purchaser
during checkout.** The scope note this ADR responds to ("providing sponsor
name/contact/logo at checkout-adjacent upload") could be read as the sponsor
uploading their own logo mid-checkout. Investigating the existing media
pipeline (`media/application/MediaUploadService.kt`,
`media/application/MediaAssignmentService.kt`) found every upload/assignment
endpoint requires `MembershipService.requireManagerRole` — there is no
public/anonymous upload path anywhere in this codebase, for any entity type,
today. Building one is a real, non-trivial security surface (presigned-URL
issuance to unauthenticated callers, abuse/rate-limiting, content moderation
for a channel org admins don't control) that this proof-of-concept slice
should not silently take on as a side effect of a one-line scope bullet. The
public checkout request therefore only collects `sponsorName`/
`sponsorContactEmail` (mirrors `Contribution`'s `supporterName`/
`supporterEmail`); an org admin uploads/assigns the logo afterward via
`SponsorshipPackageController.assignSponsorLogo`, exactly the same shape as
`ProductController.assignDesign` manages a product's design image. `Sponsor`
is still its own table (not inline fields on `Sponsorship`, unlike
`Contribution`) specifically so it has a stable id to hang that
`MediaEntityType.SPONSOR`/`MediaUsageSlot.SPONSOR_LOGO` assignment off of.

**"Sold out" is computed, not stored: `confirmedCount >= effectiveMaxQuantity`,
where an `exclusive` package's effective max is forced to 1 regardless of a
separately-configured `maxQuantity`.** A package can't simultaneously claim
to be the single exclusive holder of a slot and allow multiple purchasers, so
`exclusive: true` overrides whatever `maxQuantity` value is set (validated at
the domain layer, `SponsorshipPackage.effectiveMaxQuantity()`, not a database
check constraint, since it depends on two columns together). Sold-out
checking counts `CONFIRMED` sponsorships only, matching how
`ContributionService`/`OrderService` already accept a small oversell race
against concurrently `PENDING` checkouts as a proof-of-concept-scale gap, not
a silent bug.

**No refunds this slice** — same precedent as Phase 3/4 slice 1, which also
shipped without refunds and added them in a later slice (Phase 5, per
ADR-017). `SponsorshipStatus.REFUNDED` exists in the schema now purely for
forward compatibility (a later slice's refund flow has somewhere to land
without a migration), the same way `V15__ledger_and_refunds.sql` added
`REFUNDED` to `contribution`/`order` ahead of Phase 5 actually building
refund logic for those.

## Consequences

- `sponsorship` reuses the org's existing Connect account and transfer
  mechanism end to end — no new payout-account concept, no new config.
- The ledger's entry-type list does not grow for this slice; only
  `LedgerSourceType` grows (`SPONSORSHIP` added to the existing
  `CONTRIBUTION`/`ORDER`/`TRANSFER`/`REFUND` set). A future slice that needs
  sponsorship-specific reporting distinct from contribution reporting would
  need to revisit this and introduce a dedicated entry type — not assumed
  here.
- A public visitor completing a sponsorship purchase never uploads a file to
  Rally26 directly. If a later slice wants true self-service logo upload
  at checkout, it requires designing a public-upload security model from
  scratch (this ADR explicitly does not sketch one) — not a small addition to
  the existing manager-role-only media pipeline.
- Approval workflow, renewal reminders, invoices, link/QR tracking, and
  sponsor-contact CRM beyond name/email all remain unbuilt — full §13
  catalog territory for a later slice, exactly as Phase 3/4 slice 1 also
  deferred their equivalent full-catalog features.
- Refund UI/API, dispute handling, and the 14-day refund window
  (ADR-017's policy) do not yet apply to sponsorships — a later slice must
  extend `SponsorshipService`/`LedgerService` the same way `ContributionService.refund`/
  `OrderService.refund` already work, once this proof-of-concept is
  validated with a real pilot sponsor.

## Alternatives Considered

- **A separate sponsorship payment/payout account**: rejected for this
  slice — no concrete requirement for separately-timed sponsorship payouts
  exists yet, and building one now would be speculative infrastructure ahead
  of any real sponsor relationship needing it.
- **A new `SPONSORSHIP` ledger entry type** (rather than reusing
  `CONTRIBUTION`'s shape with a new source type): rejected — the accounting
  is identical to a contribution's, and DESIGN-DOC.md §8.6's entry-type list
  is explicitly meant to grow only when a genuinely different computation
  rule is needed, not for every new revenue category.
- **Self-service sponsor logo upload during public checkout**: rejected for
  this slice — would require designing and building the codebase's first
  public/anonymous file-upload path (presigned URLs, abuse protection,
  moderation) as an unplanned side effect of this feature, when the existing
  admin-managed pattern (already proven for product design images) fully
  covers the "sponsor gets a logo displayed" requirement without that new
  surface.
- **Storing a precomputed `sold_out` boolean on `sponsorship_package`**:
  rejected — it would need to be kept in sync with every confirmation, an
  extra place for a bug to hide, when computing it from `confirmedCount` and
  `effectiveMaxQuantity()` at read time is cheap and always correct.

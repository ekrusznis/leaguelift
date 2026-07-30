# ADR-037: Phase 13 Slice 2 — Test-Coverage Gap Review

## Status
Accepted

## Context

ADR-035 scoped Phase 13 Slice 2 to checking DESIGN-DOC.md section 18.1's 21
named "critical scenarios to always cover" against what's actually tested
today, writing real tests only for genuine gaps found. A full audit of all 21
scenarios found: 13 already solidly covered, one covered only by manual code
review (no automated regression test), five real test gaps against existing,
already-correct behavior, one gap that is actually a missing *feature* rather
than a missing test, and one gap that needs infrastructure this repo doesn't
have. Per the founder's explicit direction mid-review, this slice adds tests
only for functionality that already exists — it does not build the two
missing pieces themselves.

## Decision

**Fixed — audit-on-mutation was stubbed but never verified (scenario 9).**
`EventServiceTest.kt`'s `cancel`/`postpone`/`update`/`detachFromSource` tests
and `EventRsvpServiceTest.kt`'s `submit` tests all stubbed
`auditService.record(...)` with `every { ... } just runs` but never asserted
it was actually called — MockK does not fail on an unused stub, so these
tests would keep passing even if the audit call were deleted from those
methods. Only `create`'s test had a real `verify(exactly = 1)`. Added the
missing `verify(exactly = 1) { auditService.record(...) }` to every one of
those test bodies — a currently-real behavior (audit-on-cancel/postpone/
update/RSVP-change) that had no regression protection.

**Added — an athlete cannot view a sibling's schedule (scenario 5).**
`AuthorizationService.hasParticipantCapability` already keys strictly off the
exact `participantId`, so a caller's own self-link to a *different*
participant already correctly returns `false` for a sibling's id — but no
test exercised this negative case; only the positive "linked athlete views
their own schedule" case existed. Added
`listForParticipant denies an athlete's self-link from viewing a sibling's
schedule` to `EventServiceTest.kt`.

**Added — tournament TBD fields can be filled in without changing event
identity (scenario 8).** `EventService.update()` always calls
`eventRepository.update()` (an in-place `UPDATE ... WHERE id = ?`) and
re-fetches by the same `eventId` — there was never an insert-based path here,
but nothing proved it. Added `update fills in TBD opponent, time, and area on
a tournament child event without changing its identity` to
`EventServiceTest.kt`, asserting the same `id` comes back and that
`eventRepository.insert(...)` is never invoked.

**Added — a fulfillment failure must not erase a paid order (scenario 14).**
`OrderService.confirmFromWebhook` marks the order `CONFIRMED` before calling
`submitFulfillment`, and `submitFulfillment` catches `RestClientException`/
`Exception` internally (recording `FulfillmentStatus.FAILED`) rather than
letting it propagate and roll back the surrounding `@Transactional` method —
this was real code with zero test coverage. Added
`confirmFromWebhook keeps the order CONFIRMED even when Printify draft-order
creation fails` to `OrderServiceTest.kt`.

**Added — public store field/status exposure (scenario 17).**
`ProductService.listPublicProducts`/`listPublicVariants`/`getPublicDesignUrl`
— the three methods `StorePublicController` uses to build its response — had
zero tests. `listPublicProducts`' `ACTIVE`-only filter and
`getPublicDesignUrl`'s PUBLIC-visibility gate are both real, already-correct
logic that had never been exercised. Added three tests to
`ProductServiceTest.kt`. (Field-level exposure — that `costMinor` never
reaches a public response — is enforced by `PublicProductVariantResponse`'s
own narrow constructor in `StorePublicController.kt`, which structurally
cannot serialize a field it doesn't declare; no controller test harness
exists for this controller yet, and building one was judged out of proportion
to this scenario's actual risk.)

**Added — an archived public page is not publicly accessible (scenario 18).**
`PublicPageService.getPublic()` already uses an allow-list check
(`if (page.status != PageStatus.PUBLISHED) throw NotFoundException`), which
already correctly blocks `ARCHIVED` — the existing test only ever exercised
this with a `DRAFT` page. Added `getPublic throws NotFoundException for
archived pages` to `PublicPageServiceTest.kt`. (Also checked `CampaignService`/
`StoreService`/`SponsorshipPackageService`'s equivalent public-read methods —
all three already use the same allow-list pattern, `ACTIVE`/`COMPLETED`/
`PUBLISHED` only, so `ARCHIVED` was never actually reachable there either; no
fix needed.)

**Added — no test-profile auth bypass, and an unauthenticated visitor is
correctly rejected (scenarios 3 and 16).** Every existing "integration test"
in this codebase (e.g. `OrganizationIsolationIntegrationTest`) autowires
service beans directly against a real Postgres instance — none of them
actually go through `SecurityConfig`'s real servlet filter chain. Added a new
`SecurityConfigIntegrationTest.kt`, the first in this codebase to issue real
HTTP requests (via the JDK's built-in `HttpClient`, since Spring Boot 4 no
longer bundles `TestRestTemplate` in `spring-boot-test` by default) against a
real running instance: an unauthenticated request to `GET /api/v1/me` returns
401; an unauthenticated request to `GET /actuator/health` still returns 200;
and a freshly-registered user's real JWT successfully authenticates against
`/api/v1/me`. This is the first test that would actually catch a bypass being
introduced in `SecurityConfig`/`JwtConfig`, rather than relying on manual code
review.

**Added — money never uses floating point, and ledger entries stay
append-only (scenarios 15 and 19).** Both were previously "true by
inspection" with no regression protection. Added `MoneyArithmeticTest.kt`
with two reflection-based checks: one asserts none of `LedgerEntry`,
`FeeBalance`, `FeeAdjustment`, `FeePayment`, `Order`, `OrderItem`,
`Contribution`, `Sponsorship`, `Campaign`, or `ProductVariant` declares a
`Double`/`Float`-typed property; the other asserts `LedgerEntryRepository`
exposes no `update`/`delete`/`modify`-named method beyond the one allowed
exception, `markIncludedInTransfer` (which only ever sets the payout-linking
column, never amount/direction/type).

**Left as documented gaps, not built this slice:**
- **Scenario 12, partial refunds reversing proportionally**: this is not a
  missing test — `OrderService.refund()`/`ContributionService.refund()` only
  support a single full refund of the entire gross amount; there is no
  partial-amount parameter anywhere in the codebase, and DESIGN-DOC.md itself
  lists "handle partial refunds" as a design target, not a shipped rule.
  Writing a test for behavior that doesn't exist would be fabricating
  coverage for a feature — this needs a founder decision on the actual
  proportional-split formula before it can be built or tested.
- **Scenario 21, migration from "the latest prior release"**: every backend
  test starts from an empty Testcontainers Postgres instance and applies
  every Flyway migration V1→latest in one pass (which does give real,
  if incidental, coverage of scenario 20, clean-database migration). There is
  no snapshot of a prior release's schema anywhere in this repo to migrate
  forward *from*, and no tooling exists to create one — this needs a real
  release-tagging/schema-snapshot process this repo doesn't have yet, the
  same class of infrastructure gap ADR-035 already flagged as pending.

## Consequences

- Five of section 18.1's 21 scenarios gained real, previously-nonexistent
  test coverage against code that was already correct; the sixth (audit
  verification) turned five look-covered-but-aren't tests into ones that
  would actually fail on a regression.
- `SecurityConfigIntegrationTest.kt` establishes the first real-HTTP
  integration test pattern in this codebase (plain JDK `HttpClient` against
  `@LocalServerPort`, since `TestRestTemplate` isn't available in Spring
  Boot 4's default `spring-boot-test` dependency) — future tests that
  genuinely need to exercise the servlet filter chain itself, not just a
  service bean, now have a template to follow.
- Two scenarios remain genuinely open, written down rather than silently
  dropped: partial refunds need a founder product decision before they're
  buildable, and prior-release migration testing needs release-snapshot
  tooling this repo doesn't have.

## Alternatives Considered

- **Building partial-refund support now to make scenario 12 testable**:
  rejected — out of this slice's scope (a test-coverage gap review, not a
  feature slice) and the founder explicitly redirected mid-session to only
  fix or add tests for functionality that already exists.
- **Building a controller-level test harness for `StorePublicController`
  to prove field-level DTO exposure**: rejected for this slice — the DTO's
  own narrow constructor already makes a cost-field leak structurally
  impossible, and no test harness exists yet for this controller; the
  service-level tests added here cover the actually-risky, previously-real
  logic gaps (status filtering, visibility gating).

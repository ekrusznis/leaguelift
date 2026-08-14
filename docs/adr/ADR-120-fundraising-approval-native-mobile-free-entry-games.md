# ADR-120: Fundraising approval, native mobile, and free-entry promotional games

## Status

Accepted — 2026-08-14

## Context

Rally26's original fundraising implementation was owner/administrator managed and used Stripe Checkout for public contributions. Phase 42 added starter templates, QR sharing, and a paid sports-box proof of concept. ADR-106 later embedded Fundraising, Swag Shop, and Sponsorships in the native app through authenticated WebViews to reach mobile parity quickly.

Founder review changed the fundraising product contract in four material ways:

1. The organization owner remains the fundraiser authority, but coaches and guardians should be able to create and edit fundraisers within their authorized team/household scope.
2. Owner approval before activation is an organization setting, defaulting on, rather than an unconditional workflow requirement.
3. Fundraising management must have first-class native mobile UI with phone/tablet/resizable layouts instead of remaining a whole-feature WebView.
4. Sports squares, brackets, drawings, predictions, and similar engagement should not be modeled as paid entries. Rally26 must keep game participation structurally separate from fundraising contributions so a donation is never required for an entry and never improves odds or entry entitlement.

The implementation also needs scheduled starts, automatic end-state handling, auditability, QR sharing, Help Center coverage, and payment-provider safety. Stripe's restricted-business guidance covers games of skill/chance with prizes, sweepstakes, office pools, and charity raffles; Rally26 therefore must not assume that merely calling an activity a fundraiser makes provider-hosted payment processing acceptable.

## Decision

### Authority and approval

- An organization owner is the approval authority for fundraisers.
- `organization_fundraising_settings.require_owner_approval` controls whether a non-owner activation request must enter `PENDING_APPROVAL`; the default is `true`.
- Owners may approve or return a pending fundraiser to draft.
- Coaches may create/edit fundraisers only in scopes authorized by their team capabilities.
- Guardians may create organization-wide fundraisers and team fundraisers only where an active guardian/participant/team relationship authorizes that team.
- Backend authorization is authoritative. Web and mobile consume returned permission flags and do not infer mutation rights from role labels.

### Campaign lifecycle

The supported lifecycle is:

`DRAFT -> PENDING_APPROVAL -> SCHEDULED -> ACTIVE -> ENDED -> CLOSED -> ARCHIVED`

Branches are allowed where appropriate:

- Approval not required / Owner activation with a current start date: `DRAFT -> ACTIVE`.
- Approved/requested fundraiser with a future start date: `DRAFT` or `PENDING_APPROVAL -> SCHEDULED`.
- A scheduler moves `SCHEDULED -> ACTIVE` when the start date arrives and `ACTIVE -> ENDED` after the configured end date.
- `CLOSED` is an explicit owner closeout; automatic expiration does not silently perform operational closeout.
- `COMPLETED` remains a legacy API/database compatibility value and maps to `CLOSED` for new closeout behavior.
- Lifecycle transitions and fundraising settings changes remain auditable.

### Templates and public sharing

- Templates are starter behavior/content, not separate payment systems.
- Supported template keys include General, In-Person Event, Sponsor Match, Milestone Challenge, Fundraising Challenge, Bake Sale, and Car Wash. `BOX_POOL` remains readable only for legacy data and is not the direction for new paid-entry use.
- Every fundraiser has a stable public URL and QR-sharing flow; QR codes encode the Rally26 public URL rather than a temporary processor session.
- Event-oriented fundraisers may include venue/location information.

### Free-entry promotional games

A `fundraising_game` and its `fundraising_game_entry` rows are separate from `contribution` records.

- Supported game types begin with Big Game Squares, Bracket Challenge, Prediction Challenge, Free Prize Drawing, and Trivia Challenge.
- A public entry request has no price, contribution ID, payment ID, or processor session.
- No purchase or donation is required to enter.
- A donation cannot add entries, improve odds, unlock a more favorable square/position, or otherwise improve prize eligibility.
- Management may configure an entry cap and entries-per-person, but those limits apply independently of donation behavior.
- Public entry responses never expose entrant email addresses.
- Winner selection/recording does not transfer prize money through Rally26.

### Payment safety

- Ordinary non-random fundraising may continue to use Rally26's supported Stripe contribution flow.
- Creating/attaching a promotional game disables provider-hosted online contributions on that campaign under the current conservative payment-safety policy. The public campaign response exposes the availability state and reason.
- The backend fails closed if a disabled campaign attempts Stripe contribution checkout, and a database trigger preserves the same invariant when a game is attached.
- Manual/offline contributions remain contributions only; recording cash/check/external money never creates a free-game entry or changes odds.
- Rally26 does **not** treat Venmo, cash, check, or another rail as a legal/compliance bypass for paid 50/50 raffles, paid sports pools, or other regulated games. Any future paid-entry/regulated mode requires a separate founder decision, legal/licensing review, payment-provider approval, and a new ADR before activation.

### Mobile architecture

The Fundraising portion of ADR-106 is superseded:

- Authenticated fundraising management is native React Native/Expo UI.
- Web and mobile share the Spring Boot API/business rules, not UI code.
- Layouts respond to actual window size so phone, tablet, foldable, and resizable screens are first-class targets.
- Public supporter donation/game pages remain web-accessible so QR recipients do not need a Rally26 account or installed app.
- ADR-106 remains in force for Swag Shop and Sponsorships until separately superseded.

### Communications and support

- Submission, approval/return, activation, and end lifecycle events use the existing transactional outbox/email infrastructure.
- Action Center and immutable audit/activity surfaces provide in-app operational visibility without introducing a second notification store solely for fundraising.
- Fundraising Help Center content is required for the web and native mobile Help experiences.
- Help article Markdown remains a safe subset; image/GIF/video/PDF article media is supported without interpreting raw HTML.
- Terms must describe free-entry mechanics precisely and prohibit using optional donations or alternate payment rails as disguised entry fees.

## Consequences

### Positive

- Volunteer creators can launch fundraising work without transferring final authority away from the organization owner.
- Organizations can choose whether approval friction is appropriate for them.
- Web/mobile behavior stays consistent because authorization, lifecycle, payment safety, and audit rules live on the backend.
- Free-game engagement can evolve independently from the financial ledger and payment providers.
- The public API can clearly explain when online contributions are unavailable rather than presenting a broken checkout.
- Native mobile fundraising removes a major whole-feature WebView exception and supports responsive device-specific UX.

### Tradeoffs

- Disabling provider-hosted contributions on a campaign that also contains a promotional game is intentionally conservative and may reduce conversion until a legally/provider-reviewed combined flow is approved.
- Existing `BOX_POOL` records need compatibility handling even though the product direction has changed.
- A true visual bracket engine, live sports scoring, regulated raffle licensing, prize fulfillment, and external winner payouts are not implied by the free-game foundation.
- Terms and Help Center content describe the product mechanics but do not replace organization-specific legal advice or required official promotion rules.

## Alternatives Considered

### Keep paid sports boxes/50-50 entries and switch from Stripe to Venmo or cash

Rejected. Changing payment rails does not remove the underlying legal/provider classification and would make Rally26 responsible for facilitating a paid-entry mechanic through a less-auditable path.

### Keep Fundraising in the ADR-106 WebView

Rejected for authenticated management. The founder requires parity-quality native screens on iOS, Android, tablets, foldables, and resizable layouts. Only specialized provider/editor surfaces should justify future WebView exceptions.

### Require owner approval for every fundraiser with no organization setting

Rejected. Owner authority is preserved while the organization can intentionally remove the approval step for trusted creators.

### Store free-game entry as a contribution with a zero-dollar amount

Rejected. That would preserve an unnecessary financial coupling and make it easier for future code to accidentally condition participation on payment. Free entries are a separate domain object.

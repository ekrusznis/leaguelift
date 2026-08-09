# ADR-089: Phase 28.3 Organization Settings Consolidation

**Status:** Accepted
**Date:** 2026-08-09

## Context

Rally26 already has authoritative organization-owned configuration and management surfaces for organization profile/address/timezone, branding/media, public pages, family credit and markup, payouts, fees/payments, financial operations, announcements/messages, events/documents, Swag Shop/fundraising/sponsorships, integrations, subscription billing, onboarding/corrections, and audit history.

Creating a second monolithic organization-settings backend would duplicate authorization, persistence, audit behavior, and provider boundaries. Phase 28 instead requires one Settings control surface that helps an authorized owner/administrator reach the real domain owners.

## Decision

1. `/app/settings` gains a capability-filtered Organization Settings directory.
2. An organization appears only when the signed-in account already has `org.manage` or `org.payout.manage`, or when a Platform Administrator already holds an active reasoned support-access session for that organization.
3. The directory groups existing controls into:
   - Organization & Branding;
   - Financial & Credits;
   - Communications;
   - Events & Participation;
   - Commerce / Swag Shop;
   - Integrations;
   - Billing & Subscription;
   - History / Administration.
4. Each group links to the existing routed domain surface. Phase 28.3 does not copy organization values into `user_preference`, create a generic organization JSON settings bucket, or add parallel write endpoints.
5. A payout-only capability sees only the existing payout settings entry point; ordinary coaches, guardians, and athletes do not receive owner/admin organization-setting groups.
6. Platform Admin support mode does not grant access by itself. The Settings directory includes the customer organization only while the existing reasoned support-access session is active; the organization route remains the backend/frontend authorization boundary.
7. Phase 28.3 adds no new organization notification-default, event-default, fee-default, payment-method, financing, tax, refund, or eligibility/waiver toggle unless a current domain service actually consumes that setting. There is no such new consumer introduced by this slice.
8. Individual SMS consent, required transactional communication, Phase 25 guardian visibility/SafeSport controls, Phase 16 profile-correction boundaries, and Phase 27 audit visibility remain non-overridable.

## Consequences

- No Flyway migration is required for 28.3.
- No OpenAPI contract changes are required for 28.3.
- Existing domain services remain authoritative and independently testable.
- The Settings page becomes the discoverable control surface without broadening permissions or inventing inactive controls.
- Future organization defaults must be introduced as typed, domain-consumed settings rather than generic key/value data.

# ADR-091: Phase 28.5 Settings QA, Authorization, and Closeout

**Status:** Accepted
**Date:** 2026-08-09

## Context

Phase 28 introduced one authenticated Settings entry point while preserving existing domain ownership and authorization:

- 28.0 owns CI/CD/source-integrity stabilization;
- 28.1 added typed personal appearance persistence;
- 28.2 added typed optional notification preferences and individual SMS consent;
- 28.3 consolidated organization settings as capability-filtered links to existing domain owners;
- 28.4 defined the future Phase 31 payment-choice/financing contract without implementation.

The closeout must prove that Settings is a control surface, not a new privilege boundary or generic configuration backend.

## Decision

### 1. Personal Settings remains self-scoped

The personal endpoints remain under `/api/v1/me/...` and take the account identity only from the authenticated `CurrentUser` principal. No request body/path parameter can select another account.

Covered endpoints:

- `GET/PATCH /api/v1/me/preferences`;
- `GET /api/v1/me/notification-preferences`;
- `PATCH /api/v1/me/notification-preferences/{topic}`;
- `PATCH /api/v1/me/sms-consent`.

The closeout test calls the controllers with distinct authenticated user IDs and verifies the service receives only the current caller's ID.

### 2. Organization Settings does not broaden organization authority

The Settings directory continues to require existing `org.manage` or `org.payout.manage` capability, or an already-active reasoned Platform Support session. It does not create organization membership, grant capabilities, or bypass the destination route/service authorization.

Closeout tests assert:

- full organization managers receive the complete eight-group directory;
- payout-only access receives only the narrowed payout/financial entry;
- callers without management capability receive no organization directory;
- organization-scoped links cannot leak a different organization ID;
- no Phase 30 eligibility/waiver or Phase 31 payment-provider control is accidentally exposed as an implemented setting.

### 3. Notification and safety boundaries remain unchanged

The Phase 28.2 policy remains authoritative:

- optional in-app defaults on;
- optional email defaults on subject to legacy guardian compatibility when applicable;
- account SMS requires current individual consent **and** explicit topic enablement;
- required account/security/invitation/financial/legal communications remain outside optional preferences;
- Phase 25 guardian-visibility/SafeSport requirements remain stronger than optional Messages preferences;
- preferences are evaluated before immutable recipient snapshots are written, and later changes do not rewrite history.

28.5 does not add another notification model.

### 4. Appearance remains presentation-only

`SYSTEM`, `LIGHT`, and `DARK` remain the only persisted appearance values. System mode follows the device preference on authenticated routes. Appearance changes do not grant access or create an audit event.

### 5. Help Center closeout

V68 adds two accurate published Help Center articles:

- a public/authenticated overview of personal Settings, appearance, notification defaults, SMS consent, and required communications;
- an owner/admin article explaining that Organization Settings links to existing domain-owned controls and does not bypass permissions.

No provider/payment article is published for Phase 28.4 because those methods are design-only and not user-facing features yet.

### 6. OpenAPI closeout

No OpenAPI change is required in 28.4 or 28.5. The last Settings API contract change remains 28.2. The closeout gate is to rerun Redocly/security validation against the branch and fix the actual reported validator/security findings rather than weakening the checks.

### 7. UI cleanup

The temporary “Coming next in Phase 28” card is removed from `/app/settings` now that 28.4/28.5 are complete. No future payment-provider control or placeholder is shown.

### 8. Operational completion remains gated by CI/CD

Phase 28 product implementation is complete through 28.5 when this slice is applied and local suites pass. **Operational Phase 28 closeout is not declared until the existing 28.0 PR gates are green.**

The currently known external checks still requiring authoritative GitHub Actions confirmation are:

- frontend;
- dependency review;
- OpenAPI YAML/security review.

A failure in one of those jobs remains Phase 28.0 work and must be diagnosed from its concrete failing step. Do not mark the phase production-ready merely because 28.1–28.5 source changes compile locally.

## Required closeout matrix

Before declaring Phase 28 fully complete:

1. owner/admin can open `/app/settings`, persist appearance/notification choices, and see only organizations they manage;
2. multi-organization manager sees each manageable organization independently with no cross-org link leakage;
3. payout-only role sees only payout/financial settings for its authorized organization;
4. ordinary coach sees personal settings but no owner/admin organization directory unless separately granted an organization-management capability;
5. guardian sees only personal settings and household-authorized downstream modules;
6. controlled athlete sees only personal settings and athlete-authorized downstream modules;
7. Platform Admin without active support access does not gain customer organization settings;
8. Platform Admin with active reasoned support access sees only that supported organization and remains the authenticated Platform Admin actor;
9. `SYSTEM`/`LIGHT`/`DARK` persists and System tracks device appearance;
10. notification defaults/explicit states/SMS consent behave as ADR-088 defines;
11. required communications cannot be disabled;
12. historical announcement/message recipient snapshots remain unchanged after later preference edits;
13. Settings mutations do not broaden Phase 27 History visibility;
14. Help Center Settings articles are visible to the intended audiences;
15. backend test + ktlint, frontend typecheck/test/lint/build, Redocly/security, dependency review, and production deploy gates are green.

## Consequences

- V68 is content-only Help Center coverage; no new business setting is introduced.
- No generic settings JSON/key-value table exists.
- No organization write endpoint is duplicated.
- No Phase 31 payment choice is activated.
- Phase 29 remains QuickBooks scaffolding-only after Phase 28 closes.

# Subscription / Plan Gating Matrix

## Owner access gate

Current `main` does contain a mobile owner gate:

- `mobile/src/app/_layout.tsx`
- `mobile/src/app/owner/_layout.tsx`
- `mobile/src/features/ownerOnboarding/routing.ts`
- `mobile/src/app/web-embed.tsx`

The gate unlocks the native Owner workspace only when all of these are true:

1. an organization exists;
2. organization status is `ACTIVE`;
3. onboarding `currentStep` is `COMPLETE`;
4. `completedAt` exists;
5. subscription status is one of `ACTIVE`, `TRIALING`, or `PAST_DUE`.

The browser/WebView Stripe return is not authoritative by itself; webhook-backed server
state is intended to unlock access.

| State | Native Owner workspace | Expected mobile behavior |
|---|---|---|
| no organization | LOCKED | resume Organization |
| organization draft / PLAN | LOCKED | resume Plan |
| REVIEW | LOCKED | resume Review & Checkout |
| CHECKOUT / checkout pending | LOCKED | remain setup/pending |
| ACTIVE + COMPLETE + org ACTIVE | UNLOCKED | native Owner workspace |
| TRIALING + COMPLETE + org ACTIVE | UNLOCKED | native Owner workspace |
| PAST_DUE + COMPLETE + org ACTIVE | UNLOCKED | keep access; billing recovery required |
| CANCELED / SUSPENDED | LOCKED | fail closed / recovery boundary |
| INCOMPLETE | LOCKED | fail closed |

## Plan entitlements

Current backend `PlanEntitlementService` implements these plan gates:

| Entitlement | Starter | Club | League/custom |
|---|---|---|---|
| Team limit | 3 | unlimited | unlimited |
| SMS | blocked | allowed | allowed |
| SportsEngine | blocked | allowed | allowed |
| TeamSnap | blocked | allowed | allowed |
| QuickBooks Online | blocked | allowed | allowed |
| Other non-gated integrations | allowed subject to normal auth | allowed | allowed |

`League` is contact-only in the plan catalog. The entitlement service currently treats
every non-STARTER plan as unrestricted for these four gates.

## AI QA vs backend QA

The App Testing agent can validate the *visible* upgrade-required UX and that forbidden
controls are not misleading. Backend tests must still prove the service actually rejects:

- fourth-team creation on Starter
- gated integration connection on Starter
- plan-gated SMS behavior

Do not rely on hidden buttons as authorization.

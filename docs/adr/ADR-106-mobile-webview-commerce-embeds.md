# ADR-106 — WebView embedding for Swag Shop, Fundraising, and Sponsorships

**Status:** Accepted
**Date:** 2026-08-10

## Context

Immediately after all four mobile personas shipped (ADR-104/105), the founder asked for a systematic gap check against web: "after owner is committed/pushed we need to verify app can do everything that web can and if not, we begin those steps," with the eventual goal of full parity "including stripe, printify, swag shops, support help center and ticketing, etc." A research pass found 9 real web feature areas with zero mobile equivalent, sized S through L. Presented with the sizes, the founder made the call directly: rather than rebuild Swag Shop, Fundraising, and Sponsorships (the most expensive gaps, L and M–L) natively, embed the real existing `frontend/` pages for those three inside an in-app WebView. QuickBooks stays web-only with zero mobile screen — its own Intuit OAuth core is inactive on web too, so there is nothing live to reach. Everything else (Help Center, owner-side Documents, Action Center) stays a candidate for a later native rebuild, not WebView.

## Decision

**Session handoff needed no new backend endpoint.** Reading `frontend/src/auth/AuthContext.tsx` and `frontend/src/lib/apiClient.ts` directly showed web's session lives in `sessionStorage` under key `"rally26.session"` as `{accessToken, expiresAt, user:{displayName,email}}`, and nothing else gates access — no cookie, no CSRF/double-submit token. The WebView is authenticated by injecting that exact JSON via `injectedJavaScriptBeforeContentLoaded`. `mobile/src/features/auth/AuthContext.tsx` gained one new export, `getWebSession()`. This is the first mobile persona/feature effort that touched `AuthContext.tsx` but needed no backend change at all beyond an environment variable.

**Stripe checkout needed zero new mobile code**, because web itself only does `window.location.href = checkoutUrl` to Stripe's hosted Checkout page — no Stripe SDK integration exists anywhere in this codebase. The WebView embedding the frontend page naturally carries the user through checkout and back; `web-embed.tsx` watches the WebView's navigated URL for `status=success`/`status=canceled` to show a native toast.

**Built:** `mobile/src/app/web-embed.tsx` (shared screen, takes `path`/`title` router params; new `react-native-webview` dependency), `mobile/src/lib/webEmbed.ts` (route-builder helper), new `EXPO_PUBLIC_FRONTEND_BASE_URL` environment variable across `.env.local`/`.env.example`/all three `eas.json` profiles — the real production frontend origin (`https://rally26.com`) was confirmed by reading `backend/src/main/resources/application-prod.yml`'s CORS allowlist rather than guessed. Entry points: Owner's More tab gained Swag Shop/Fundraising/Sponsorships (pointing at the owner-management frontend sections); Coach's and Parent's More tabs both gained Swag Shop (pointing at the buyer/personalization/checkout order-flow route specifically, a different frontend path from the owner-management route for the same feature).

**A real backend constraint found and accepted, not routed around:** the authenticated in-app Swag Shop order flow's Stripe success/cancel redirect is hardcoded server-side to `frontendProperties.baseUrl` (`OrderService.createSwagShopCheckoutSession`) — no caller-supplied override exists, unlike Fundraising/Sponsorship's checkout endpoints, which do accept `successUrl`/`cancelUrl`. Since the redirect stays inside the same WebView regardless of which URL it targets, this is a non-issue for the WebView approach specifically — flagged in `mobile/README.md` as a known limitation only because it forecloses a native deep-link handoff for that one flow without a backend change later.

**Verification:** typecheck/lint/expo-doctor all clean, same bar as every prior mobile ADR.

## Consequences

- Swag Shop, Fundraising, and Sponsorships reached mobile parity in one slice instead of three separate native rebuilds.
- QuickBooks stays intentionally absent from mobile — not a gap, a direct consequence of it being inactive on web too.
- Help Center, owner-side Documents, and Action Center remain open, native-first candidates, not committed to WebView by this decision.
- `react-native-webview` requires a native rebuild (`npx expo run:android`/`ios`), not just `npm start` — the first mobile dependency with that requirement, a real constraint for the live-device QA that followed (ADR-107).

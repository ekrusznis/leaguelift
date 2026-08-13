# Rally26 full-site UX/sales review

**Status:** Not started (inventory built 2026-08-13)
**Requested by:** Founder, after the Stripe-fee/reorder/sync-redesign/household-media/Help-Center batch
**Deliverable shape:** This document. A page/flow inventory (below) walked in-browser, top to bottom, by a reviewer acting as a sales-and-UX engineer — not a code reviewer. Findings get logged inline in the **Notes** column as they're found; this doc is not necessarily fixed inline during the pass itself (fixes get scoped and prioritized afterward).

## Why

The product has real depth now — payments, fundraising, Swag Shop, sponsorships, eligibility/waivers, a platform admin console, a mobile app — built incrementally across many phases, each correct in isolation. This pass checks whether it *feels* like one cohesive product: consistent placement, clear actions, plain language, working links, and a UI that doesn't feel like it was assembled feature-by-feature.

## What "good" looks like — review criteria

For every page/flow in the inventory below, check:

1. **Placement & hierarchy** — is the primary action the most visually prominent thing on the page? Is related content grouped sensibly? Does the page match the layout conventions of sibling pages in the same persona?
2. **Actions** — do all buttons/links do what their label promises? Are destructive actions (cancel, archive, remove, refund) appropriately distinguished from routine ones? Are disabled states explained (not just greyed out with no reason)?
3. **Text** — is copy plain-language and specific (not generic placeholder-sounding text)? Consistent terminology with the rest of the app (e.g., "household" vs "family," "organization" vs "club")? No leftover Lorem-ipsum, debug strings, or internal jargon leaking into user-facing copy?
4. **Links** — do all links resolve (no dead routes, no `href="#"` left over from a stubbed feature)? Do external links open sensibly (new tab where appropriate)?
5. **Empty / loading / error states** — does every list/table have a real empty state (not a blank void)? Do loading states avoid layout jump? Do error states explain what happened and offer a next step (retry, contact support), not a raw error code?
6. **Responsiveness** — does the page hold up at a real mobile width, not just desktop? (Rally26 also has a native mobile app for several personas — flag anything that assumes desktop-only interaction on a page still reachable from a phone browser.)
7. **Dark mode** — full inversion, no light-surface card left un-styled against a dark page (per [[feedback-dark-mode-full-inversion]] — this has been a recurring finding in past passes).
8. **Fluidity** — does completing the primary task on this page take a reasonable number of clicks/screens? Any place a user would plausibly get stuck, confused about what happens next, or have to guess at unlabeled icons?
9. **Cross-persona consistency** — where the same concept appears in multiple personas' UI (e.g., "Fees & Payments" for both Owner and Parent), does it look and behave consistently, adjusted only for the different permissions/data each persona sees?

Use a **Status** value per row: `Not reviewed` (default) / `Clean` (checked, nothing wrong) / `Issues found` (see Notes) / `Fixed` (issue found and resolved same-pass).

---

## Public / marketing (no sign-in required)

| Page | Route | Status | Notes |
|---|---|---|---|
| Home | `/` | Not reviewed | |
| Solution detail | `/solutions/:slug` | Not reviewed | Check every solution slug actually linked from Home resolves |
| Talk to Sales | `/talk-to-sales` | Not reviewed | `/founding-pilot`, `/book-demo` redirect here — confirm redirect copy still makes sense |
| Contact | `/contact` | Not reviewed | Redirects — confirm destination |
| Security | `/security` | Not reviewed | |
| Help Center (public) | `/help` | Not reviewed | Search, category filter |
| Support request (public) | `/help/support` | Not reviewed | Category list, confirmation state |
| Help article (public) | `/help/:slug` | Not reviewed | Spot-check the 3 articles added in V86 (`swag-shop-reordering`, `updating-an-event-from-its-source`, `household-media-center`) plus older ones for embed rendering now that image/video embeds exist |
| Privacy Policy | `/privacy` | Not reviewed | |
| Terms of Service | `/terms` | Not reviewed | |
| Accessibility statement | `/accessibility` | Not reviewed | |
| Public campaign (fundraiser) page | `/campaigns/:slug` | Not reviewed | Contribution flow, attribution/anonymous choice |
| Public Swag Shop store | `/swag-shop/:slug` | Not reviewed | |
| Public athlete storefront | `/swag-shop/athlete/:slug` | Not reviewed | |
| Public sponsorship page | `/sponsors/:slug` | Not reviewed | Package purchase flow |
| Public org/team/tournament page | `/p/:slug` | Not reviewed | Branding (logo/cover/colors), all three page types |
| 404 | `/404`, unmatched routes | Not reviewed | |

## Authentication

| Page | Route | Status | Notes |
|---|---|---|---|
| Sign in | `/auth/sign-in` | Not reviewed | |
| Register (owner) | `/auth/register` | Not reviewed | |
| Resend verification | `/auth/resend-verification` | Not reviewed | |
| Forgot password | `/auth/forgot-password` | Not reviewed | |
| Reset password | `/auth/reset-password` | Not reviewed | |
| Verify email | `/auth/verify-email` | Not reviewed | |
| Accept invitation | `/auth/invitation` | Not reviewed | Staff, guardian, athlete invitation variants |
| Auth error | `/auth/error` | Not reviewed | |

## Shared authenticated shell (every persona)

| Page | Route | Status | Notes |
|---|---|---|---|
| Dashboard (persona-specific) | `/app` | Not reviewed | See per-persona dashboard sections below — same route, different content per context |
| Owner onboarding wizard | `/app/onboarding/:step?` | Not reviewed | Multi-step; check step-back/resume behavior |
| Action Center | `/app/action-center` | Not reviewed | |
| Announcements | `/app/announcements` | Not reviewed | |
| Messages | `/app/messages` | Not reviewed | |
| Audit History | `/app/history` | Not reviewed | |
| Personal Integrations | `/app/integrations` | Not reviewed | |
| Settings | `/app/settings` | Not reviewed | Appearance, notification preferences |
| Help Center (authenticated) | `/app/help`, `/app/help/support`, `/app/help/:slug` | Not reviewed | |
| Organizations list | `/app/organizations` | Not reviewed | Only reachable for users with multiple org memberships |
| Organization detail (context switch) | `/app/organizations/:organizationId` | Not reviewed | |
| Event detail | `/app/organizations/:organizationId/events/:eventId` | Not reviewed | Include the new "update available from source" banner/dialog (Track 3) on a source-linked event |
| Team events | `/app/organizations/:organizationId/teams/:teamId/events` | Not reviewed | |
| Team roster | `/app/organizations/:organizationId/teams/:teamId/roster` | Not reviewed | |
| Tournament events | `/app/organizations/:organizationId/tournaments/:tournamentId/events` | Not reviewed | |
| Participant events (athlete schedule) | `/app/organizations/:organizationId/participants/:participantId/events` | Not reviewed | |
| Organization billing | `/app/organizations/:organizationId/billing` | Not reviewed | |
| Collections | `/app/organizations/:organizationId/collections` | Not reviewed | |
| Disputes | `/app/organizations/:organizationId/disputes` | Not reviewed | |
| Swag Shop order flow | `/app/organizations/:organizationId/swag-shop/order` | Not reviewed | Include Reorder and the vendor-unavailable dialog (Track 2) |

## Owner / Director (ORGANIZATION context) — organization sections

Route shape: `/app/organizations/:organizationId/:section`

| Section | Status | Notes |
|---|---|---|
| Overview | Not reviewed | |
| Onboarding (checklist) | Not reviewed | |
| Corrections | Not reviewed | Profile correction request review |
| Teams | Not reviewed | |
| Tournaments | Not reviewed | |
| Households & Athletes | Not reviewed | |
| Fees & Payments | Not reviewed | |
| Fundraising | Not reviewed | |
| Swag Shop | Not reviewed | |
| Financial operations | Not reviewed | |
| Sponsorships | Not reviewed | |
| Events | Not reviewed | |
| Reports | Not reviewed | Includes the new Stripe-fee-visibility metrics (Track 1) |
| Documents | Not reviewed | |
| Eligibility | Not reviewed | Waivers/eligibility evidence (Phase 31) |
| Members | Not reviewed | |
| Organization Integrations | Not reviewed | |
| Settings | Not reviewed | |
| Organization dashboard (Owner nav item) | Not reviewed | `OwnerDashboard.tsx` — the `/app` landing content for this persona |

## Parent / Guardian (HOUSEHOLD context)

Route shape: `/app/organizations/:organizationId/households/:householdId/:section`

| Section | Status | Notes |
|---|---|---|
| Household Profile | Not reviewed | |
| My Athletes (participants) | Not reviewed | |
| Fees & Payments | Not reviewed | |
| Family Schedule (events) | Not reviewed | |
| Documents | Not reviewed | |
| Photos & Videos (media) | Not reviewed | **New this batch (Track 5)** — upload flow, multi-select, "Release publicly" dialog copy and repeat-every-time behavior |
| Correction Requests | Not reviewed | |
| Parent dashboard | Not reviewed | `ParentDashboard.tsx` — the `/app` landing content, including the `#parent-fundraising` section |

## Athlete (ATHLETE context)

| Page/section | Status | Notes |
|---|---|---|
| Athlete dashboard | Not reviewed | `AthleteDashboard.tsx` — `/app` landing content |
| Schedule | Not reviewed | `/app/organizations/:organizationId/participants/:participantId/events` |
| My Teams (`#athlete-teams`) | Not reviewed | Dashboard section, not a separate route |
| Profile & Guardians (`#athlete-profile`) | Not reviewed | Dashboard section |

## Coach (TEAM context)

| Page/section | Status | Notes |
|---|---|---|
| Coach dashboard | Not reviewed | `CoachDashboard.tsx` — `/app` landing content |
| My Teams (`#coach-teams`) | Not reviewed | Dashboard section |
| Schedule | Not reviewed | `/app/organizations/:organizationId/teams/:teamId/events` |
| Roster Summary (`#coach-roster`) | Not reviewed | Dashboard section |
| Team Roster (full page) | Not reviewed | `/app/organizations/:organizationId/teams/:teamId/roster` |
| Team Page editor (`#coach-team-page`) | Not reviewed | Dashboard section — branding/publish |
| Fundraising (`#coach-fundraising`) | Not reviewed | Dashboard section |
| Swag Shop order flow | Not reviewed | Same route as Owner/Parent's, team-scoped |

## Tournament Admin (TOURNAMENT context)

| Page/section | Status | Notes |
|---|---|---|
| Tournament dashboard | Not reviewed | `TournamentDashboard.tsx` — `/app` landing content. Note: no Messages nav item for this persona by design — confirm that doesn't read as a bug |
| Schedule & Events | Not reviewed | `/app/organizations/:organizationId/tournaments/:tournamentId/events` |
| Tournament Page (`#tournament-page`) | Not reviewed | Dashboard section |
| Settings (`#tournament-summary`) | Not reviewed | Dashboard section |

## Platform Admin (PLATFORM_ADMIN context)

Route shape: `/app/platform/:section`

| Section | Route | Status | Notes |
|---|---|---|---|
| Platform dashboard | `/app` | Not reviewed | `PlatformAdminDashboard.tsx` |
| Organizations | `organizations` | Not reviewed | |
| Organization console (single org) | `organizations/:organizationId` | Not reviewed | Support-session entry point |
| Subscriptions | `subscriptions` | Not reviewed | |
| Users | `users` | Not reviewed | |
| Data Integrity (duplicates) | `data-integrity/duplicates` | Not reviewed | |
| Integration Operations | `operations` | Not reviewed | |
| Reports | `reports` | Not reviewed | Cross-org financial reports, Stripe-fee margin (Track 1) |
| Audit | `audit` | Not reviewed | |
| Support Sessions | `support-sessions` | Not reviewed | Reasoned-access entry/exit flow |
| Help Articles (authoring) | `help-articles` | Not reviewed | **New this batch (Track 4)** — attachment picker (image/GIF/video/PDF), insert-embed flow, Markdown body editor |
| Support Cases | `support-cases` | Not reviewed | |
| Swag Shop (cross-org) | `swag-shop` | Not reviewed | |
| Payments (cross-org) | `payments` | Not reviewed | Refund/void actions |
| Athletes & Coaches (roster) | `roster` | Not reviewed | Table/card toggle |

## Cross-persona / multi-step flows worth walking end-to-end

These span several of the pages above — worth a dedicated pass rather than only checking each page in isolation.

| Flow | Status | Notes |
|---|---|---|
| Owner self-registration → org setup → Stripe subscription checkout → onboarding checklist | Not reviewed | DESIGN-DOC.md §15 journey #1 |
| Staff invitation → accept → role-scoped dashboard | Not reviewed | §15 journey #2 |
| Household creation → guardian invitation → guardian claims household | Not reviewed | §15 journey #3 |
| Fee assignment (org side) → fee payment (guardian side) → receipt | Not reviewed | §15 journeys #7-8 |
| Fundraiser creation → public contribution → credit availability | Not reviewed | §15 journeys #9-10 |
| Swag Shop product setup → purchase → **Reorder → vendor-unavailable dialog** | Not reviewed | Extends §15 journey #11 with Track 2's new step |
| Event creation → ICS/CSV source sync stages a change → **review/apply from source** | Not reviewed | Extends §15 journey #12 with Track 3's redesign |
| Household media upload → multi-select → **release publicly** | Not reviewed | New this batch (Track 5) |
| Help article creation → **attach image/video/PDF → insert embed → publish → public view renders it** | Not reviewed | New this batch (Track 4) — walk the full author-to-reader loop, not just the editor |
| Support case submission → Platform Admin triage → resolution email | Not reviewed | §15 journey #17 |
| Guardian e-sign waiver (Phase 31 eligibility) | Not reviewed | |
| Mobile app parity spot-check | Not reviewed | Not a route in this table (native app, not a web route) — worth at least a light pass per [[rally26-mobile-web-parity-gap-analysis]] to catch anything that drifted since that analysis |

---

## After the pass

Roll findings up into a short punch list (blocking vs. cosmetic) rather than leaving them scattered across this table — that's the actual deliverable for prioritization. Do not fix in-line during the browsing pass itself unless a finding is trivial and directly in the way of continuing the review; log everything else here first (see [[feedback-qa-pass-workflow]] for the established convention: fix only blockers live, log the rest).

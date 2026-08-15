# Rally26 full-site UX/sales review

**Status:** In progress (inventory built 2026-08-13; expanded with real per-feature filter/list/action detail 2026-08-13; mobile app coverage added 2026-08-13; heuristic product/UX review + build-health validation added 2026-08-13; **delta re-review completed 2026-08-14 to retire stale findings after the search/sort/filter/mobile-parity push**; live browser/device walkthrough still pending — full web parity required, QuickBooks the sole exception)
**Requested by:** Founder, after the Stripe-fee/reorder/sync-redesign/household-media/Help-Center batch
**Deliverable shape:** This document. A page/flow inventory walked in-browser, top to bottom, by a reviewer acting as a sales-and-UX engineer — not a code reviewer. Every page row below now carries the *real, currently-built* filters/sort/bulk-actions/columns/empty-state for that page (gathered by reading the actual components, not guessed), so a reviewer checks the page against its own real behavior, not a blind wishlist. Findings get logged inline in the **Notes** column as they're found; this doc is not necessarily fixed inline during the pass itself (fixes get scoped and prioritized afterward).

## Why

The product has real depth now — payments, fundraising, Swag Shop, sponsorships, eligibility/waivers, a platform admin console, a mobile app — built incrementally across many phases, each correct in isolation. This pass checks whether it *feels* like one cohesive product: consistent placement, clear actions, plain language, working links, and a UI that doesn't feel like it was assembled feature-by-feature.

## Delta re-review (2026-08-14)

This refresh specifically re-checked the places that had aged fastest since the 2026-08-13 inventory, especially the old cross-cutting list-control findings and the mobile parity notes. The big takeaway: **several of the sharpest “operational UX” gaps are no longer current and should be struck from the active punch list.**

### Can now be struck / retired from the active punch list

1. **Web/mobile build-health blockers are no longer current.** The fundraising regressions that made the last pass unsafe to browse were fixed; the 2026-08-13 stability section is now historical context, not the current state.
2. **“No search almost anywhere” is no longer true.** Teams, Households, Fee Templates, Collections, Events, Campaigns, Contributions, Orders, Team Roster, and the new Organization Members roster all now have real list controls.
3. **“No sort anywhere except Audit History” is no longer true.** Sort controls now exist across multiple owner/operator-heavy surfaces.
4. **Platform Admin zero-result blank states were fixed.** Help Articles and Support Cases now both render real empty states instead of a blank void.
5. **The “no active-members roster” gap is resolved.** `InvitationsPanel` now embeds `OrganizationMembersPanel`, giving organizations a real active-member/staff roster with role changes and disable-access actions.
6. **Several mobile parity blockers are no longer current.** Help Center, support request submission, Action Center, owner-side Documents, guardian messaging-safety controls, and mobile event create/edit now exist.

### Still remaining

1. **List controls are still uneven, just no longer absent.** Tournaments remain the clearest holdout, and several older owner-facing panels (Sponsorship packages, Organization Documents, Eligibility requirements, Disputes, Adults/Participants, household Fee Assignments) still lag behind the newer toolbar/search/pagination pattern.
2. **Bulk actions are still unusually rare.** Household media public-release and message recipient selection remain the only real multi-select/bulk patterns found in product code.
3. **Mobile parity is much better, but not complete.** Household media, family credit application/transfer, org profile / credit-settings edit, and owner team/tournament create-edit-archive still appear missing.
4. **Documentation drift is now its own trust risk.** `mobile/README.md` and parts of this document had already fallen behind the shipped app (for example, they still described Action Center / Help / owner Documents as missing after those screens landed).

### New / newly clearer follow-ups

1. **The new list-toolbar pattern needs consistency review, not invention.** The work now exists; the next question is whether placeholder text, filter naming, sort defaults, result counts, and empty states feel uniform enough across Teams, Households, Fees, Collections, Fundraising, Orders, Events, and Members.
2. **The owner/member-management story should now be reviewed as a whole flow.** There is finally a real active-members roster; the next pass should verify whether the split between “Active members & staff” and “Pending invitations” reads naturally in-browser.
3. **Mobile parity notes now need a real code-read, not just README-based assumptions.** This refresh already corrected several stale assumptions from the earlier mobile section.

## Cross-cutting findings from the inventory pass (2026-08-13, before any live browsing)

These emerged independently, by the same pattern, across every feature domain researched — not isolated to one page. Worth the founder's attention as product-level decisions, not per-page nitpicks.

1. **The app is no longer “searchless,” but list-control maturity is still uneven.** Real free-text search now exists on Teams, Households, Fee Templates, Collections, Events, Campaigns, Contributions, Orders, Team Roster, Organization Members, plus the Platform Admin console and Audit History. The remaining gap is now **consistency**, not total absence: Tournaments, Sponsorship packages, Disputes, Organization Documents, Eligibility requirements, Adults/Participants, and several financial-ops panels still lack the same control surface.
2. **No bulk-select/bulk-action UI exists anywhere except two places**: the household media "Release publicly" multi-select (Track 5, this session), and the multi-recipient picker when starting a family conversation in Messages. Every other action (waive, cancel, verify, approve, reject, archive, publish, refund) is single-row only, everywhere in the app.
3. **Sort controls now exist on several high-value operational lists, but not everywhere.** Teams, Households, Fee Templates, Collections, Events, Campaigns, Contributions, Orders, Team Roster, Organization Members, and Audit History all expose sorting now. The remaining issue is again uneven adoption across older panels.
4. **Pagination is improving and is no longer mostly invisible, but it is still inconsistent.** The newer search-driven lists now tend to pair `ListToolbar` with visible `Pagination`, while some older screens still render `data.items` directly or keep fixed-size admin lists with no visible pager.
5. **Money formatting is inconsistent under the hood** — ad hoc per-file `formatAmount`/`formatMoney`/`humanize` helpers alongside a shared `formatMoneyMinorUnits`, rather than one component everywhere. Not user-visible as a bug unless two pages actually render the same amount differently; worth a quick cross-check.
6. **Dead code found**: `frontend/src/features/reporting/PlatformOrganizationsPage.tsx` is a second, simpler "Organizations" table that is **not wired into `AppRoutes.tsx`** — superseded by `frontend/src/features/platformAdmin/PlatformOrganizationsPage.tsx`. Not a UX issue (nothing renders it), but worth deleting rather than leaving as confusing dead code.
7. **This is resolved:** `PlatformHelpArticlesPage` and `PlatformSupportCasesPage` now render explicit zero-result empty states.
8. **This is resolved:** there is now a real active-member roster via `OrganizationMembersPanel`, embedded above pending invitations in `InvitationsPanel`.

## Heuristic review summary (docs + code + validation pass, 2026-08-13)

This section is **not** a substitute for the live browser/device walkthrough below. It is the founder-ready readout from reviewing the current product intent (`README.md`, `DESIGN-DOC.md`), the implemented web/mobile shells and dashboards, and the existing frontend/mobile validation checks.

### Overall impression

**The good news:** Rally26 already reads as a serious, trustworthy, premium youth-sports operations product more than it reads as a side-project admin panel. The brand system is coherent (deep navy + green + restrained gold), the marketing site is visually stronger than a typical early-stage B2B app, and the shared dashboard shells show real care around hierarchy, accessibility, and role-aware navigation.

**The risk:** once a user moves past the first impression, the experience becomes much more operational and table-driven than sales-site-polished. The application feels strongest when it is acting like a guided dashboard, and weakest when it becomes a dense admin workspace without search/sort/bulk tools. In other words: the product currently looks more trustworthy than it feels effortless.

### What already feels strong

1. **Brand/visual identity is coherent.** The marketing shell, auth pages, dashboard chrome, and card system all use a consistent tone. Nothing obvious in the reviewed code suggests a clashing second design language.
2. **Navigation intent is honest.** The dashboard context label is correctly no longer a fake switcher; capability-filtered nav avoids teasing inaccessible destinations; public/auth/app shells are clearly separated.
3. **Marketing first impression is credible.** `HomePage.tsx` presents a modern, premium, B2B product posture rather than a juvenile sports aesthetic, which fits the founder/treasurer/director buyer better.
4. **Shared-state UX discipline exists.** The codebase has real empty states, loading states, and guarded destructive actions in many places — a sign that the product is maturing beyond happy-path demos.
5. **Public monetization flows are product-complete enough to review seriously.** Fundraising, Swag Shop, sponsorship, public pages, and Help Center are substantial enough that a true top-to-bottom UX pass is worth doing now.

### What currently weakens trust or ease of use

1. **The biggest remaining risk is consistency, not raw stability.** The regression set from the previous pass is now fixed; what can still trip a reviewer is inconsistent control patterns between newer toolbar-driven pages and older low-control screens.
2. **The product's information architecture is broad, but not uniformly ergonomic.** Many owner/managers can now search/sort, but several important lists still lack the newer toolbar, visible pagination, or a clear bulk story.
3. **There is still an expectation gap between the sales promise and day-to-day admin ergonomics.** The public site feels curated and premium; some operational areas still feel utilitarian and row-by-row.
4. **Mobile parity is materially better, but not complete.** Several foundational workflows now exist, but the remaining gaps are still real and should be reviewed as product work, not mere polish.
5. **Documentation drift remains a trust risk.** Both this doc and `mobile/README.md` had to be updated after the last review because they were already behind the shipped app.

## Stability findings from the validation pass (2026-08-13)

These are important because they directly affect how credible the product feels during a top-to-bottom review.

**2026-08-14 update:** this section is now a historical snapshot. The fundraising regression set called out below has since been fixed and should not remain on the active UX blocker list.

1. **Web app build health was red in the 2026-08-13 snapshot, but is no longer the current blocker.** At that time, `npm run typecheck`, `npm run test`, and `npm run build` all failed around the fundraising slice.
2. **The breakage centers on fundraising-related work plus a few unrelated regressions.** Concrete examples from the failing checks:
   - `src/features/fundraising/fundraising/CampaignList.tsx` has unresolved imports / typing issues.
   - `src/pages/OrganizationDetailPage.tsx` passes a `canManage` prop that no longer matches `CampaignList`'s current prop contract.
   - `src/dashboard/roles/PlatformAdminDashboard.tsx` has a `Link` wrapper typing bug.
   - `src/test/setup.ts` is stale versus the current DOM lib (`IntersectionObserver.scrollMargin`).
3. **Mobile app build health was also red in the 2026-08-13 snapshot, but is no longer the current blocker.** At that time, `npm run typecheck` and `npm run lint` both failed around newly-added fundraising screens/routes.
4. **The mobile breakage is concentrated in newly-added fundraising screens/routes.** Concrete examples from the failing checks:
   - `src/app/fundraising.tsx`, `src/app/fundraising-detail.tsx`, `src/app/fundraising-form.tsx`, and `src/app/fundraising-game.tsx` reference unresolved `@/features/fundraising/*` and `@/features/fundraisingGames/*` modules.
   - several `router.push`/`router.replace` calls target paths Expo Router does not currently recognize in the generated route typing.
5. **Implication for UX review (updated):** the fundraising-specific build blockers should be struck from the active review checklist; the higher-value work now is validating whether the newly-added list controls and mobile screens actually feel cohesive in-browser/in-app.

## Current UX punch list (post delta re-review)

### P0 — blockers / trust-damaging issues

1. **Align public-facing status docs with the actual product.** `README.md` still says only Phases 0-2 are complete, while the application and `DESIGN-DOC.md` clearly contain much more. A reviewer/customer noticing that mismatch loses confidence fast.
2. **Keep the “no broken-but-clickable mobile controls” rule active.** Even though the previous Event Details example is now fixed, the rule still matters product-wide: every mobile control should be disabled with context, open a working screen, or open a working WebView — never end in a placeholder toast.
3. **Prevent the new list-control patterns from drifting out of sync.** The newer toolbar pages are good enough to review, but they still need a pass for consistent copy, filter names, pagination behavior, and empty-state language so the app feels designed rather than accumulated.

### P1 — major UX improvements likely to pay off fastest

1. **Add list ergonomics to the remaining holdouts.** Search first, then sort, then selective bulk actions. Highest-value candidates from this inventory:
   - Tournaments
   - Adults / Participants / household fee assignments
   - Disputes
   - Sponsorship packages / sponsors
   - Documents
   - Eligibility requirements
2. **Stress-test the new active-members roster for clarity.** The product gap is closed; the live pass should now verify whether role-editing, disable-access, and pending-invitation management feel like one coherent access-management surface.
3. **Tighten the owner dashboard's action hierarchy.** It already has many cards plus multiple quick actions; confirm the one next-best action for a new owner is obvious rather than merely available.
4. **Standardize empty/zero-result states across the newer toolbar pages.** The Platform Admin blank-state bug is fixed; the next check is whether the new “No results found / Try changing your search or filters” pattern is consistent enough everywhere it now appears.
5. **Audit terminology for consistency:** household vs family, owner vs administrator, organization vs club, fundraiser vs campaign, storefront vs Swag Shop.

### P2 — parity and flow quality

1. **Close the mobile parity gaps that are now explicitly in-scope product work:**
   - Household media
   - family credit application / transfer where supported
   - org profile / credit-settings edit
   - owner team/tournament create/edit/archive
2. **Review every WebView-embedded flow for "native-shell fit" rather than only functional parity.** Back behavior, auth handoff, keyboard handling, loading state, and post-Stripe return are as important as the underlying web page.
3. **Reduce row-by-row fatigue in operational workflows.** The product has depth now; if every admin action remains a single-record action, larger organizations will feel friction sooner than they feel delight.

## Recommended live-review order

To get the highest-value findings quickly, walk in this order once the current build regressions are resolved enough to browse safely:

1. **Public first impression:** Home → Solutions → Pricing/CTA → Help Center → one public article
2. **Owner new-customer journey:** Register → Stripe checkout/resume → onboarding → org overview
3. **Owner operations core:** Teams → Households → Fees & Payments → Fundraising → Swag Shop → Sponsorships → Reports → Documents → Members
4. **Guardian reality check:** Parent dashboard → fees → schedule → documents → media → fundraiser/share link
5. **Coach speed/usability:** dashboard → team switcher → schedule → roster → fundraising
6. **Platform Admin trust check:** organizations → support console → help articles → support cases → payments → audit
7. **Mobile pass by persona:** Parent first, then Owner, then Coach, then Athlete; only then test WebView embeds and Stripe return behavior

## What "good" looks like — review criteria

For every page/flow in the inventory below, check the criteria below **and** the concrete filter/list/action detail now recorded in that row's Notes:

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
| Action Center | `/app/action-center` | Not reviewed | No filters/sort/bulk. Two summary tiles (Open items, High or urgent). Rows: priority badge (Urgent/High/Action needed/Review), title, description, relevant date. Per-row: single "Open" link to the source record. Empty: "You are caught up." |
| Announcements | `/app/announcements` | Not reviewed | **Inbox** (no filter/sort/bulk): New badge, scope, published date, title, body; per-row "Mark read." Empty: "No announcements." **Manage (staff only)**: filters = Communication scope select, Status select (All/Draft/Published/Archived); per-row Edit (draft)/Publish (draft)/Archive. Composer: title/body/audience select/email+SMS checkboxes. |
| Messages | `/app/messages` | Not reviewed | **Inbox** (split-pane, no filter/sort/bulk): thread title, unread badge, thread type, scope, last-message time, 2-line preview; per-message Mark read/Report, reply form. **Start conversation**: recipient picker has Select all/Clear all + checkboxes (the only other real bulk-select in the app besides household media). **Manage (staff)**: filters = Scope select, Status select (All/Open/Archived); per-row Archive/Send update (broadcasts). |
| Audit History | `/app/history` | Not reviewed | The richest filter set in the app: From/Through date, Action (free text), Result select, Keyword, plus permission-gated User/Team ID/Organization ID fields — staged as a draft, applied only on "Apply." Sort: Date/Action/Result × Ascending/Descending (the **only** page in the app with a real sort control). Cursor-based Previous/Next, 50/page. Read-only rows, no per-row actions. Empty: "No history matches these filters." |
| Personal Integrations | `/app/integrations` | Not reviewed | |
| Settings | `/app/settings` | Not reviewed | Appearance, notification preferences |
| Help Center (authenticated) | `/app/help`, `/app/help/support`, `/app/help/:slug` | Not reviewed | |
| Organizations list | `/app/organizations` | Not reviewed | Only reachable for users with multiple org memberships |
| Organization detail (context switch) | `/app/organizations/:organizationId` | Not reviewed | |
| Event detail | `/app/organizations/:organizationId/events/:eventId` | Not reviewed | Include the new "update available from source" banner/dialog (Track 3) on a source-linked event |
| Team events | `/app/organizations/:organizationId/teams/:teamId/events` | Not reviewed | |
| Team roster | `/app/organizations/:organizationId/teams/:teamId/roster` | Not reviewed | See Coach section below — same `TeamRosterPage` component |
| Tournament events | `/app/organizations/:organizationId/tournaments/:tournamentId/events` | Not reviewed | |
| Participant events (athlete schedule) | `/app/organizations/:organizationId/participants/:participantId/events` | Not reviewed | |
| Organization billing | `/app/organizations/:organizationId/billing` | Not reviewed | |
| Collections | `/app/organizations/:organizationId/collections` | Not reviewed | `CollectionsPage`. Search: household / athlete / fee / template. Filters: Status select, "Overdue only" checkbox. Sort: Due date (asc/desc), Balance (asc/desc), Household, Fee name, Newest, Oldest. Real `<table>`: Household (link)/Participant/Description/Original/Paid/Adjusted/Balance/Due/Status. Action: **Export current results**. Empty: "Nothing to collect" / "No results found." Visible pagination now exists. |
| Disputes | `/app/organizations/:organizationId/disputes` | Not reviewed | No filters/sort/bulk. Real `<table>`: Source (contribution/order/sponsorship/fee)/Amount/Reason/Opened/Evidence due/Status. Explicitly read-only — evidence handled in the Stripe Dashboard, not here. Empty: "No disputes." Flag: unlike every other financial list, no link back to the underlying source record from a dispute row. |
| Swag Shop order flow | `/app/organizations/:organizationId/swag-shop/order` | Not reviewed | Buyer-facing checkout/reorder flow (distinct from the org-facing Orders management under the Swag Shop organization section, below). Include Reorder and the vendor-unavailable dialog (Track 2). |

## Owner / Director (ORGANIZATION context) — organization sections

Route shape: `/app/organizations/:organizationId/:section`

| Section | Status | Notes |
|---|---|---|
| Overview | Not reviewed | |
| Onboarding (checklist) | Not reviewed | |
| Corrections | Not reviewed | `OrganizationCorrectionReviewPanel`. Filter: Status select (Pending default/Approved/Rejected/Withdrawn/All) — no text search, no sort, no bulk. Rows: target+field, current→requested value, reason, requester, timestamp, status. Per-row: Reject (disabled until note ≥3 chars), Approve (note optional) — both PENDING-only. Empty: "No matching correction requests." |
| Teams | Not reviewed | `TeamList`. Search: name / sport / season / age group / level. Filters: Sport, Season, Gender, Status. Sort: Name A–Z / Z–A, Sport, Newest, Oldest. Visible pagination. Rows: name/sport/season/age group/gender/level/status. Per-row: Schedule, Roster, Branding (inline panel), Manage access, Timezone (inline editor), Colors (inline panel), Archive. Empty: "No teams yet" / "No results found." |
| Tournaments | Not reviewed | `TournamentList`. **Still a holdout:** no filter/search/sort/bulk and no visible pagination despite sibling lists now having them. Rows: name/sport/date range/location. Per-row: Schedule, Branding, Manage access, Timezone, Archive — **no Roster action** (unlike Teams). Empty: "No tournaments yet." |
| Households & Athletes | Not reviewed | `HouseholdList`. Search: household / parent email / athlete. Filters: Team, Status. Sort: Name A–Z / Z–A, Newest, Oldest. Visible pagination. Rows: display name + contact email + status (phone captured but not shown). Per-row: "View" link only. Empty: "No households yet" / "No results found." |
| Fees & Payments | Not reviewed | `FeeTemplateList` (managers) or read-only `OrganizationReportsPanel` (report-only viewers). Search: template name or description. Filter: Status. Sort: Name A–Z / Z–A, Amount asc/desc, Newest, Oldest. Visible pagination. Rows: template name, amount, description. Per-row: Archive (no confirm dialog). Empty: "No fee templates yet" / "No results found." Links out to Disputes and Collections. |
| Fundraising | Not reviewed | `CampaignList` + nested `ContributionList`. **Campaign list now has real controls:** search (name/description/venue/address), filters (Status/Type/Team), sort (Newest, Name, Start, End, Most raised, Largest goal), visible pagination. Campaign rows: name/status/raised-of-goal/date/location; per-row QR & share, Print flyer, Contributions, Edit, Submit for approval / Approve / Return / Close / Archive depending on permissions. Contributions now also have search (supporter/email), Status + payment-source filters, sort (Newest/Oldest/Amount/Supporter), and pagination. Empty: "No fundraisers yet" / "No contributions yet" / "No results found." Coach/Parent stay role-aware read-only here; box-pool management remains manager-only. |
| Swag Shop | Not reviewed | `StoreList` (no filter/sort/bulk; rows: name/status/slug; actions: View/Activate/Manage products) → `OrderList` nested per store (no filter/sort/bulk; rows: supporter, payment-source badge, refunded badge, fulfillment status; actions: Manage fulfillment, Preview refund). Empty: "No confirmed orders yet." |
| Financial operations | Not reviewed | Three stacked panels, none with text search: **Offline records** (filters: Verification status, Record type; per-row Verify; empty "No offline financial records"), **Corrections** (no filter; form-driven Preview→Confirm refund/reversal flow; empty "No financial corrections"), **Reconciliation** (no filter; "Run reconciliation" button; per-issue "Review record" link; empty "No reconciliation run yet" / "No exceptions found"). |
| Sponsorships | Not reviewed | `SponsorshipPackageList`. No filter/search/sort/bulk on the package list. Rows: name/status/Exclusive/Sold out/price/confirmed-of-max. Per-row: Publish, Archive, Share (QR + link), Manage sponsors (nested panel: sponsor rows with review-status badge, per-row Edit contact/Preview refund) + org-wide "Review pending sponsorships" queue (Approve / Reject & refund via `window.confirm`). Empty: "No sponsorship packages yet." |
| Events | Not reviewed | Same `EventListPanel` component as Family Schedule (below), org-wide scope — mutable here (Create/manage templates available), unlike the household read-only view. |
| Reports | Not reviewed | `OrganizationReportsPanel`. Filter: From/To date range only. No sort/bulk. 4 metric tiles + 4 breakdown tables (revenue by source/team, campaign performance, product performance). Only action: **Export revenue CSV**. Includes the new Stripe-fee-visibility metrics (Track 1) on the Platform side — confirm whether the org-facing report should surface any of this too. |
| Documents | Not reviewed | `OrganizationDocumentsPanel`. No filter/search/sort/bulk (though "Send to every household" acts like a broadcast on upload). Rows: title (link) + file size. Per-row: Remove. Empty: "No documents yet." |
| Eligibility | Not reviewed | `EligibilityRequirementList`. No filter/search/sort/bulk — list is implicitly ACTIVE-only (no way to view archived/expired requirements). Rows: title/version/mode/sport/season/team/effective date. Per-row: New version (inline form, publishes rather than overwrites), Archive. Empty: "No eligibility requirements yet." |
| Members | Not reviewed | `InvitationsPanel` now starts with `OrganizationMembersPanel`, so this section is no longer pending-invitations-only. **Active members & staff**: search (name/email), filters (Role/Status), sort (Name/Role/Newest/Oldest), visible pagination; per-row role change + Disable access (non-owner active members only). **Pending invitations** remain below as a separate simple list with Send invitation / Revoke. |
| Organization Integrations | Not reviewed | |
| Settings | Not reviewed | |
| Organization dashboard (Owner nav item) | Not reviewed | `OwnerDashboard.tsx`. Cards, none with their own filter/sort/bulk (all deep-link to the real filterable page): Organization Summary, Financial Overview (some fields flagged demo data), **Team Performance table** (Team/Sport, Participants, Fundraising progress, Status — no sort/filter), Upcoming Events, Recent Activity, Reports Snapshot, Quick Actions tiles. Header: Collections & Export / Invite Member / Create Team. |

## Parent / Guardian (HOUSEHOLD context)

Route shape: `/app/organizations/:organizationId/households/:householdId/:section`

| Section | Status | Notes |
|---|---|---|
| Household Profile | Not reviewed | `AdultsPanel` (default section). No filter/search/sort/bulk. Per-row: Request correction, Remove. Page action: Add adult. Empty: "No adults on record." |
| My Athletes (participants) | Not reviewed | `ParticipantsPanel`. No filter/search/sort/bulk. Rows: photo, name, worst eligibility-clearance pill, DOB. Per-row: Request correction, Teams (assign/remove via select + chips), Eligibility (expand panel). Page action: Add participant. Empty: "No participants yet." |
| Fees & Payments | Not reviewed | `FeeAssignmentsPanel` (household-scoped). No filter/search/sort/bulk. Rows: description, balance/original, due date, participant, status badge. Per-row: Pay online (balance > 0), Send reminder (manager), Update status select → Waive/Cancel (manager), Details (expands payment/adjustment history each with a per-item Void + reason prompt). Page: Add fee (manager, template-driven), running balance, "Other ways to pay." Empty: "No fees assigned." |
| Family Schedule (events) | Not reviewed | `EventListPanel`, household scope — **read-only for everyone** here (no Create/manage-templates actions at this scope, unlike the org-wide Events section). Sort: client-side by start time only, not user-controllable. Rows: title, status pill, "Imported" badge, date/time, arrival, venue. Per-row: Add to calendar (.ics), View details. Empty: "No events yet." |
| Documents | Not reviewed | `HouseholdDocumentsPanel`. No filter/search/sort/bulk. Rows: title (link) + size. Per-row: Acknowledge (guardian), Remind household (staff), Remove (manager). Page: Add document (manager). Empty: "No documents." |
| Photos & Videos (media) | Not reviewed | `HouseholdMediaPanel`. **New this batch (Track 5)**. Bulk: Select all/Clear all + "Release publicly (N)" — the only other real bulk-action in the app besides the Messages recipient picker — opens a confirmation `Modal` before publicizing. Grid cards: checkbox, visibility label, image thumbnail or generic "🎥 Video" placeholder (no real thumbnail/frame extraction), file size. Per-card: Remove. Empty: "No photos or videos yet." Check the "Release publicly" dialog copy and that it really re-shows every time, not just once. |
| Correction Requests | Not reviewed | `HouseholdCorrectionRequestsPanel`. No filter/search/sort/bulk. Rows: target+field, current→requested diff, reason, status. Per-row: Withdraw (own PENDING requests only, matched by email). Empty: "No correction requests." |
| Parent dashboard | Not reviewed | `ParentDashboard.tsx`. Cards: My/linked Athletes, Family Schedule, Outstanding Balance, Family Credits, Active Fundraisers (each with a "Get my sharing link" toggle → `AttributionLinkPanel`), Recent Orders, **Documents (embeds the full `HouseholdDocumentsPanel` with Acknowledge — a real list, not just a summary card)**. Header: View Fees / View Fundraisers. Includes the `#parent-fundraising` section. |

## Athlete (ATHLETE context)

| Page/section | Status | Notes |
|---|---|---|
| Athlete dashboard | Not reviewed | `AthleteDashboard.tsx` — **entirely demo/placeholder data per the file's own comment** (no real participant-login concept exists yet). Cards: Next Event, My Teams, This Week, Recent History (win/loss pill), Guardians. Worth confirming with the founder whether this is expected to stay placeholder for the review pass or whether real wiring is now expected. |
| Schedule | Not reviewed | `/app/organizations/:organizationId/participants/:participantId/events` |
| My Teams (`#athlete-teams`) | Not reviewed | Dashboard section, not a separate route |
| Profile & Guardians (`#athlete-profile`) | Not reviewed | Dashboard section |

## Coach (TEAM context)

| Page/section | Status | Notes |
|---|---|---|
| Coach dashboard | Not reviewed | `CoachDashboard.tsx`. Team-selector `<select>` in the header (only if >1 team) drives every card. Cards: My Teams, Team Schedule, Roster Summary (attendance% flagged demo data), Team Page Status, Fundraising Progress (raised amount flagged demo data). |
| My Teams (`#coach-teams`) | Not reviewed | Dashboard section |
| Schedule | Not reviewed | `/app/organizations/:organizationId/teams/:teamId/events` |
| Roster Summary (`#coach-roster`) | Not reviewed | Dashboard section |
| Team Roster (full page) | Not reviewed | `TeamRosterPage`. Filter: single "Show ineligible only" pill toggle (only if `TEAM_ELIGIBILITY_VIEW`) — no text search, no sort, no bulk. Rows: name, eligibility pill. **No per-row action or link at all** — fully read-only, no link even to the participant's own profile. Two empty-state variants depending on the toggle. |
| Team Page editor (`#coach-team-page`) | Not reviewed | Dashboard section — branding/publish |
| Fundraising (`#coach-fundraising`) | Not reviewed | Dashboard section |
| Swag Shop order flow | Not reviewed | Same route as Owner/Parent's, team-scoped |

## Tournament Admin (TOURNAMENT context)

| Page/section | Status | Notes |
|---|---|---|
| Tournament dashboard | Not reviewed | `TournamentDashboard.tsx` — deliberately minimal (2 cards only: Tournament detail, Tournament Page Status), per the file's own comment that no `tournament_team` join exists yet. Note: no Messages nav item for this persona by design — confirm that doesn't read as a bug |
| Schedule & Events | Not reviewed | `/app/organizations/:organizationId/tournaments/:tournamentId/events` |
| Tournament Page (`#tournament-page`) | Not reviewed | Dashboard section |
| Settings (`#tournament-summary`) | Not reviewed | Dashboard section |

## Platform Admin (PLATFORM_ADMIN context)

Route shape: `/app/platform/:section`. This is the one persona where filtering is consistently real — every page below except the console drill-down and Reports has at least a status filter, and most also have text search.

| Section | Route | Status | Notes |
|---|---|---|---|
| Platform dashboard | `/app` | Not reviewed | `PlatformAdminDashboard.tsx`. 7 KPI tiles; **Organizations table** (Organization/Owner/Status/Members/Teams/Gross volume, fixed to first 5, no filter/sort in the widget); Organization Snapshot (for the org under active support access); Recent Audit Activity (top 5); Support Access card; Operational Queue Overview; Platform Health (4 tiles). |
| Organizations | `organizations` | Not reviewed | Text search (name/slug/contact email) + Status select. No sort, no bulk. Columns: Organization/Status/Members/Teams/Households/Athletes. Per-row: "Open console." Empty: "No organizations match these filters." |
| Organization console (single org) | `organizations/:organizationId` | Not reviewed | Not a list — 8 metric tiles, contact detail, "Start support access" form (reason, min 10 chars), then 13 module tiles that stay disabled until support access is active. Support-session entry point. |
| Subscriptions | `subscriptions` | Not reviewed | Text search (org name/slug) + Subscription status select. No sort/bulk. Columns: Organization/Plan/Status (+cancel-at-period-end flag)/Recovery/Stripe customer+subscription links/last failure-success. Per-row: "Open org" only — explicitly read-only billing visibility. Empty: "No organizations match these filters." |
| Users | `users` | Not reviewed | Text search (name/email) + Status select. No sort/bulk. Columns: User/Status/Platform role/Organizations & roles/Created. No direct per-user action button (only inline org links). Empty: "No users match these filters." |
| Data Integrity (duplicates) | `data-integrity/duplicates` | Not reviewed | Master/detail, not a plain table. Text search (email/phone) on candidate groups. Detail: identity cards with Source/Surviving-target toggle, "Build resolution preview," then "Resolve duplicate identity" gated on active support session + reason (10-500 chars) + typed confirmation of the target's email. Empty: "No duplicate keys match this search" / "Select a candidate group to review it." |
| Integration Operations | `operations` | Not reviewed | Dashboard + one real table (no filter/sort/bulk on the table itself): 4 metric tiles, provider readiness/contract-hardening cards, "Recent provider runs" list (first 10, non-interactive), then an "Outbox exception queue" table (Event/Status/Organization/Attempts/Last error) with a per-row **Reprocess** button. Empty: "No failed or dead-letter events" / "No integration sync runs recorded." |
| Reports | `reports` | Not reviewed | Filter: From/To date range only. No sort/bulk. 10 metric tiles only, no tables/rows — includes the new Stripe-fee margin figures (Track 1). **Flag**: unlike the org-facing report, there is no CSV export here. No "no data" empty-state treatment for an all-zero period (falls back to a generic error/retry state on failure only). |
| Audit | `audit` | Not reviewed | Thin wrapper around the same `AuditHistoryPage` as the personal Audit History above — same rich filter/sort set, plus platform-wide scope. |
| Support Sessions | `support-sessions` | Not reviewed | Filter: Status select only (All/Active/Ended/Expired) — no text search. No sort/bulk. Columns: Employee/Organization (link)/Reason/Status/Started/Expires-ended. Empty: "No support sessions match this status." |
| Help Articles (authoring) | `help-articles` | Not reviewed | **New this batch (Track 4)** — attachment picker (image/GIF/video/PDF), insert-embed flow, Markdown body editor. List: single free-text search (title/content), fixed page size 100, no visible category/audience filter. Zero-result state now exists ("No results found" / "No help articles yet"). Actions: New article, Save draft, Publish, Archive. |
| Support Cases | `support-cases` | Not reviewed | Text search + Status select, fixed page size 100. No sort/bulk. Card layout (not a table): category/subject/requester/org/description, then per-case editable Status/Priority/Assigned-to/Resolution note. Actions: Save case, Send email (one-way composer, no reply thread). Zero-result state now exists ("No results found" / "No support cases yet"). |
| Swag Shop (cross-org) | `swag-shop` | Not reviewed | Text search (product/store/org) + Status select. No sort/bulk. Columns: Organization/Team-Store/Product/Status/Variants/Logo ready. Per-row: "Open organization" only — explicitly read-only, status/delete changes must happen inside the org's own Swag Shop section. Empty: "No Swag Shop products match these filters." |
| Payments (cross-org) | `payments` | Not reviewed | Refund/void actions. Text search (payer/org/team) + Type select + Status select + From/To date. No sort/bulk. Columns: Organization/Team/Type/Payer/Amount/Status/Date. Per-row: Refund/Void (label depends on type, `window.confirm` guard, requires active support session for that org) + "Open organization." Empty: "No payments match these filters." |
| Athletes & Coaches (roster) | `roster` | Not reviewed | Table/card toggle. Person-type tabs (Athletes/Coaches) + text search + Eligibility status select (athletes only). No sort/bulk. Athlete columns: name+DOB/Organization/Household/Teams/Eligibility. Coach columns: name+email/Organization/Team/Role. Per-row: "Open organization" only, explicitly read-only. Empty: "No {athletes|coaches} match these filters." |

## Mobile app (native, `mobile/` — Expo/React Native, 4 personas: Coach, Parent, Athlete, Owner)

**Parity requirement (founder decision, 2026-08-13): mobile must have all the same functionality as the website. QuickBooks connection is the only confirmed, permanent exception** — its own web-side OAuth core is inactive too, so there's nothing live to reach either way. Every other web feature is in scope for mobile, whether or not it's built yet. See [[rally26-mobile-full-parity-decision]] for the full resolution of prior gap analysis into required work.

This section was originally seeded from existing mobile-build records plus `mobile/README.md`, but parts of it were refreshed by direct code read on 2026-08-14 after the parity push. **Assume older “missing entirely” claims below are stale unless they survive this refresh.**

### Shared screens (all 4 personas)

| Screen | Route | Status | Notes |
|---|---|---|---|
| Sign in | `/login` | Not reviewed | Real `POST /auth/login`, `expo-secure-store` token storage |
| First-launch onboarding | `/onboarding` | Not reviewed | 3-slide carousel, real splash/onboarding art (ADR-107) |
| Non-built-role fallback | `/role-not-available` | Not reviewed | Real `GET /me/dashboard-context`; working sign-out |
| Action Center | `/action-center` | Not reviewed | Real cross-persona action list with two summary tiles (Total, High priority), real item rows, open-destination routing, and a real empty state ("All caught up") |
| Help Center | `/help`, `/help/[slug]` | Not reviewed | Real search + category chips + article list; header action opens support request |
| Support request | `/support-request` | Not reviewed | Real case submission form plus "My recent cases" list |
| Thread detail | `/messages/[threadId]` | Not reviewed | Real messages, working send (respects `canReply`), mark-read — no filter/sort, matches web's own Messages having none either |
| My Guardians (Athlete only) | `/guardians` | Not reviewed | Real data |
| Event Details | `/event-details?id=` | Not reviewed | Real event + RSVP summary/picker (guardian-per-athlete or self); Share uses RN's native share sheet; **Edit now routes to the real `/event-form` create/edit screen instead of a placeholder toast** |
| Announcements | `/announcements` | Not reviewed | Real data, **All/Unread filter** (one of the only filters that exists anywhere in the mobile app), mark-read on open |
| Announcement detail | `/announcement-details?id=` | Not reviewed | Full body |
| Settings | `/settings` | Not reviewed | Real appearance/notification/SMS-consent, real Log Out. **Known gap**: appearance saves but many screens still hardcode dark-only hex colors in their own StyleSheets (ADR-107) — same class of issue as [[feedback-dark-mode-full-inversion]] on web, not yet audited on mobile. Worth a dedicated pass. |

### Coach persona (`/`, 5 tabs: Home/Calendar/Teams/Messages/More)

| Screen | Status | Notes |
|---|---|---|
| Dashboard | Not reviewed | Real teams + team schedule + announcements preview; team switcher modal if >1 team |
| Calendar | Not reviewed | Real month-grid math + real team events grouped by IANA timezone — no filter/sort beyond the grid itself |
| Team Roster | Not reviewed | Real participants — no "position" field (doesn't exist on the backend either, correctly not fabricated) |

### Parent/Guardian persona (`/parent`, 5 tabs: Home/Calendar/Payments/Messages/More)

| Screen | Status | Notes |
|---|---|---|
| Dashboard | Not reviewed | Real linked athletes + family schedule + outstanding balance + announcements preview |
| Family Calendar | Not reviewed | Real server-side union across every linked athlete's teams |
| Payments | Not reviewed | Real balance + itemized fees + credit balance — **read-only, no in-app payment collection** (note: web itself has no Stripe fee-checkout either, so this specifically is not a parity gap — confirmed in the original gap analysis) |
| Fee Details | Not reviewed | Per-fee payment history |
| Documents | Not reviewed | Real household document list + acknowledge — plain upload/ack, no Phase 31 eligibility/waivers concept (doesn't exist on mobile's backend contract usage yet) |
| **Household media (Photos & Videos)** | **Missing entirely** | Web shipped this today (Track 5) — guardian upload, multi-select, "Release publicly." Still no mobile equivalent found in code. |
| Messaging Safety controls | Not reviewed | Real `/safety-controls` screen now exists: guardian athlete picker + restriction kind chips + record/lift restriction flow |
| **Family credit application / P2P transfer** | **Missing entirely** | Payments screen is read-only; the real backend endpoints exist and are confirmed, just not wired to mobile |

### Athlete persona (`/athlete`, 4 tabs: Home/Calendar/Messages/More — deliberately no Teams/Payments tab, matches real backend access)

| Screen | Status | Notes |
|---|---|---|
| Dashboard | Not reviewed | Real overview + teams + own upcoming schedule |
| Calendar | Not reviewed | Real own-schedule month-grid |
| Messages | Not reviewed | Shared thread list + a real "New Conversation" flow unique to Athlete (SafeSport-gated, disabled per-team until org-approved rather than surfacing a raw 409) |

### Owner persona (`/owner`, 4 tabs: Home/Teams/Members/More)

| Screen | Status | Notes |
|---|---|---|
| Dashboard | Not reviewed | Real summary/financial-overview/team-performance/upcoming-events/recent-activity/reports-snapshot — correctly omits the two dashboard cards that are unconditionally fabricated server-side even on web |
| Teams | Not reviewed | Real org-wide team list — **read-only this slice**; no create/edit/archive |
| Team Detail | Not reviewed | Read-only fields |
| Members | Not reviewed | Real list + role update/revoke; relies on the backend's own manager-tier 403 rather than a client-side permission hide |
| Reports | Not reviewed | Real revenue/fee-collections/refunds, trailing 30 days — **no CSV export** (web has one) |
| Payout Account | Not reviewed | Real Stripe Connect status/balance — **read-only, no onboarding-link/transfer actions** (real money-movement, deliberately deferred) |
| Announcements (manage) | Not reviewed | Real list + publish |
| New Announcement | Not reviewed | Draft-then-publish, **org-scoped only** — no team/tournament-scoped compose yet |
| Broadcasts (manage) / New Broadcast / Broadcast Detail | Not reviewed | Real thread list/create/send, **org-scoped only** — no messaging to specific teams/coaches/parents yet (deferred at ADR-107 QA round) |
| Event create/edit | Not reviewed | Real shared `/event-form` screen now exists for create + edit |
| Documents (owner-side) | Not reviewed | Real `/owner/documents` screen now exists: add document, send to every household, remove |
| **Org profile / credit-settings edit** | **Missing entirely** | |
| **Team/tournament create/edit/archive** | **Missing entirely** | |

### WebView-embedded features (Swag Shop, Sponsorships — ADR-106)

Rather than native rebuilds, `/web-embed` loads the **real `frontend/` pages** for these embedded feature areas inside an in-app WebView, authenticated by injecting the same session JSON `frontend/src/auth/AuthContext.tsx` already reads from `sessionStorage` — no new backend endpoint needed. Stripe checkout rides along for free since web itself only redirects to Stripe's hosted Checkout.

**Because these are literally the web pages, their filter/sort/list/action behavior is identical to what's already documented in this doc's web sections above** (Swag Shop under Owner/Coach/Parent's Swag Shop rows, Sponsorships under the Owner organization-sections table) — no separate mobile-specific inventory needed for these embedded pages; the review question here is narrower: does the WebView wrapper itself behave well (loading state, back-navigation, the injected-session auth actually working, `status=success`/`status=canceled` toast on Stripe return, and general fit-and-finish of a web page inside a native shell — safe-area insets, keyboard behavior on the checkout form, etc.)

| Entry point | Status | Notes |
|---|---|---|
| Owner More → Swag Shop / Sponsorships | Not reviewed | Points at the owner-management frontend sections |
| Coach More → Swag Shop | Not reviewed | Points at the buyer/personalization/checkout order-flow route, not owner management |
| Parent More → Swag Shop | Not reviewed | Same buyer-flow route as Coach |
| Coach More → Fundraising / Parent More → Fundraising | Not reviewed | No longer a WebView-only note — these now route to the native fundraising screens (`/fundraising`, `/fundraising-detail`, `/fundraising-form`, `/fundraising-game`), while Swag Shop and Sponsorships remain the deliberate embedded/web exceptions |
| Help Center / Support ticketing / Action Center | Not reviewed | No longer missing: these exist as native shared screens (`/help`, `/support-request`, `/action-center`) rather than WebView wrappers |

### Imagery / assets

| Item | Status | Notes |
|---|---|---|
| Splash screen + onboarding illustrations | Not reviewed | Real founder-supplied art (ADR-107), replacing earlier icon/color-block placeholder |
| App icon (Android) | Not reviewed | Generated from `frontend/public/favicon.svg`, verified by compositing onto a visible backdrop |
| App icon (iOS, Icon Composer bundle) | **Could not be visually verified during the build** | No available tool renders the newer Xcode icon format — flagged as needing a real Xcode/iOS check before trusting it |
| Team/org logos, profile photos (native screens) | Not reviewed | Confirm these render via the same signed-URL pattern web uses, and that a missing/broken image degrades gracefully rather than showing a broken-image icon |
| Household media thumbnails | N/A | Not a mobile gap specifically — web itself has no thumbnail/frame-extraction pipeline either (founder decision, Track 5); once household media reaches mobile it should match web's "full-size image, generic video badge" approach, not invent thumbnails mobile-only |

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
| Mobile: sign in → each persona's real screens → WebView-embedded Swag Shop/Fundraising/Sponsorships checkout | Not reviewed | Walk the injected-session WebView auth end to end on a real device/emulator, not just confirm the screens render |
| Mobile parity punch list: household media, family-credit actions, owner org-profile/credit-settings edit, owner team/tournament create-edit-archive | **In progress** | Help Center, support request, Action Center, owner Documents, event create/edit, and guardian messaging-safety controls can be struck from the old mobile parity list; these remaining gaps still need live validation and prioritization |

---

## Infrastructure/scaling note (not a UX finding, recorded here per founder request 2026-08-13)

**One-liner: no — a cache (Redis) or message queue (Kafka) would be premature complexity right now, not a real gain.** Current architecture is a single droplet, single self-hosted Postgres instance (ADR-008/061), no paying clients yet (same funding-gate logic as the AI Media Lab deferral — see [[rally26-ai-media-lab-rename-and-phase-order]]), and the DB-backed outbox pattern already does the one job a message queue would do here (reliable async processing) without a second piece of infrastructure to run/monitor/secure. Redis would help with hot-read caching or rate-limiting once real concurrent load shows up, but nothing in this codebase is measured as a bottleneck today. Same logic applies to Kubernetes/Docker Swarm/multi-server-with-per-region-DB: vertical scaling (a bigger droplet) is the correct next lever, long before horizontal scaling — add any of this only when real usage data shows a specific, measured bottleneck a bigger single server can't fix, not ahead of need.

## After the pass

Roll findings up into a short punch list (blocking vs. cosmetic) rather than leaving them scattered across this table — that's the actual deliverable for prioritization. Do not fix in-line during the browsing pass itself unless a finding is trivial and directly in the way of continuing the review; log everything else here first (see [[feedback-qa-pass-workflow]] for the established convention: fix only blockers live, log the rest). The 8 cross-cutting findings above should feed directly into that punch list as product-level decisions, independent of anything the live browsing pass finds page-by-page.

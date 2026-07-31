# ADR-038: Phase 13 Slice 3 — Accessibility Code-Level Audit

## Status
Accepted

## Context

ADR-035 scoped Phase 13 Slice 3 to a code-level accessibility audit — semantic
HTML, ARIA, keyboard navigation, color contrast — using the same methodology
ADR-030 established for the Phase 11 mobile audit: static source review, not
live-browser viewport testing, since the sandboxed browser tool's viewport
control still doesn't work reliably in this environment. No ESLint
configuration exists anywhere in this repo, so this is the first accessibility
review this codebase has ever had, automated or otherwise.

The audit covered five priority areas in the same order ADR-030 used (public
pages first): public/marketing pages and checkout flows, auth pages, all six
dashboards, remaining app pages, and shared components. Overall the codebase
was found to be unusually accessibility-conscious already — correct semantic
HTML, `aria-label`s on icon buttons, `role="alert"`/`role="status"` on
feedback messages, and real `<label htmlFor>` associations were used correctly
almost everywhere, with `alt` text correct on every image in the tree (zero
missing-`alt` instances found). The real defects were concentrated in a small
number of *repeated, high-leverage* patterns rather than scattered noise.

## Decision

**Fixed — missing `aria-describedby` linking a field to its own error
message, systemic across nearly every hand-rolled form.** `aria-invalid` was
set correctly, but the error `<p role="alert">` had no `id` and the input had
no `aria-describedby` pointing at it — a screen-reader user tabbing to a
failed field heard only "invalid," never *why*. The shared `components/
forms/FormField.tsx` primitive already does this correctly; the defect was
files rolling their own `<label>`/`<input>`/error `<p>` by hand and dropping
the association. Fixed by adding a matching `id` to each error paragraph and
`aria-describedby={errors.field ? "field-id-error" : undefined}` to each
input, across 16 files: `pages/marketing/PublicCampaignView.tsx`,
`PublicSponsorshipView.tsx`, `ContactPage.tsx`, `features/publicpage/
PublicPagesPanel.tsx`, `features/teams/TeamList.tsx`, `features/tournaments/
TournamentList.tsx`, `features/households/HouseholdList.tsx`, `features/fees/
FeeTemplateList.tsx`, `features/store/StoreList.tsx`, `ProductManagementPanel.
tsx`, `features/fundraising/CampaignList.tsx`, `features/sponsorship/
SponsorshipPackageList.tsx`, `features/integrations/IntegrationsPanel.tsx`,
`features/organizations/InvitationsPanel.tsx`, `OrganizationProfileForm.tsx`,
and `pages/HouseholdDetailPage.tsx` (five separate forms in one file). This is
adoption of an existing correct pattern, not new design.

**Fixed — broken label association in `PublicPagesPanel.tsx`.** A `<label>`
with no `htmlFor` sat next to a `<select>` with no `id` — siblings, not a
real association. Added matching `htmlFor`/`id`.

**Fixed — unlabeled read-only share-link input in
`SponsorshipPackageList.tsx`.** A screen reader announced only "edit text"
with the raw URL as its value, no indication of purpose. Added
`aria-label="Shareable sponsorship link"`.

**Fixed — low-contrast text on the auth card background.** `text-slate-500`
(`#76869A`) on `bg-navy-800` (`#102B46`) computed to a ~3.87:1 contrast ratio,
below the WCAG AA 4.5:1 minimum for normal text — confirmed via the actual
hex values in `styles/tokens.css`, not eyeballed. Occurred in
`pages/auth/SignInPage.tsx` ("or continue with") and `AuthErrorPage.tsx`
("Reference: {requestId}"). Swapped to `text-slate-400` (~5.6:1, already used
elsewhere in these same components), matching the token every other
dark-background usage in the codebase already uses correctly.

**Fixed — heading hierarchy skipped from `h1` straight to `h3` on every
dashboard.** `dashboard/components/DashboardPageHeader.tsx` renders the
page's only `h1`; `dashboard/components/DashCard.tsx` rendered every widget
title as `h3` directly beneath it, with no intervening `h2` anywhere on any
of the six dashboards (confirmed via a full heading-tag grep across
`dashboard/`). Since no section-level grouping exists between the page title
and widget cards, `h2` is the correct next level — changed `DashCard`'s title
element from `h3` to `h2`, fixing all six dashboards from one shared
component. Verified live: logged into a real seeded dev account, dashboard
renders identically, no visual regression.

**Fixed — `GlobalSearchBox` results were operable by mouse only.** The
results dropdown had no `onKeyDown` handling and none of the ARIA combobox
pattern (`role="combobox"`, `aria-expanded`, `aria-controls`,
`aria-activedescendant`, `role="listbox"`/`role="option"`) — a keyboard-only
user could type a query but never navigate or activate a result without a
mouse. Implemented the full pattern: ArrowDown/ArrowUp move a tracked
highlight index (wrapping at both ends), Enter selects the highlighted
result, Escape closes the dropdown, and the highlight resets whenever the
result set changes. Verified live in a real browser against a real backend
and seeded data: typed "Soccer," confirmed two real results rendered,
pressed ArrowDown twice and read the DOM directly to confirm
`aria-activedescendant`/`aria-selected` moved correctly between "JV Soccer"
and "Varsity Soccer," then confirmed Escape actually removed the listbox
from the DOM — not inferred from source, actually exercised.

**Left as a documented gap, not built this slice — the entire dashboard
sidebar navigation is non-interactive.** Every nav item in
`DashboardShell.tsx` renders as a plain `<span>` with no `href`, `onClick`,
or `tabIndex`, inside a `<nav aria-label="Dashboard">` landmark with zero
operable controls inside it, on any of the six dashboards — root-caused in
`dashboard/registry/navRegistry.tsx`'s `NavRegistryItem` interface, which
has no route/destination field at all. This is the exact same gap ADR-030
already found during the mobile audit and explicitly declined to fix
("a pre-existing gap unrelated to mobile tuning, not something this audit
should paper over by building real navigation") — the same reasoning applies
here. Wiring real routes into the nav registry plus six dashboards' worth of
mapping is a genuine feature build (new routes, capability-filtered nav
destinations per role), not an accessibility bug fix to existing behavior,
and stays out of this slice for the same reason ADR-030 kept it out.

**Left as documented, lower-priority gaps:** dead `DashCard` action buttons
(real, focusable `<button>`s that silently do nothing when `action.to` is
omitted — the audit itself judged this a feature-completeness gap, not a
confirmed accessibility defect, since the control is semantically correct);
`AuthTabs.tsx`'s use of the ARIA ```tablist```/```tab``` pattern for what are
actually route-navigation `<Link>`s (screen readers still announce them
sensibly; the audit itself wasn't confident this rises to a hard failure);
and inconsistent `role="status"` on async success messages across a handful
of files (cosmetic — error announcements are consistently correct everywhere,
only some success confirmations lack the live-region role).

## Consequences

- 16 forms across the app now correctly announce *why* a field failed
  validation to assistive technology, not just *that* it failed.
- Every dashboard now has a real, single-level-skip-free heading hierarchy,
  fixed once in a shared component rather than six times.
- Keyboard-only users can now fully operate global search — type, navigate
  results, and select — on the Owner and Platform Admin dashboards, verified
  against real data in a real browser rather than assumed from source.
- The dashboard sidebar's total non-operability remains open, tracked in two
  independent places now (ADR-030 and this ADR) rather than forgotten — a
  future navigation-wiring slice has a clear, already-diagnosed root cause
  (`navRegistry.tsx` needs a route field) to start from.

## Alternatives Considered

- **Building real dashboard sidebar navigation now, since it's the most
  severe finding**: rejected — this is a feature build (new routes across
  six role-scoped dashboards, capability-aware destination filtering), not a
  fix to existing markup, and ADR-030 already established the precedent of
  leaving this specific gap for a dedicated slice rather than folding it
  into an audit pass.
- **Fixing every "uncertain" finding (AuthTabs' ARIA pattern, dead DashCard
  buttons, inconsistent success-message live regions) to be maximally
  thorough**: rejected — none were confirmed defects by the audit itself,
  and speculative changes to ARIA roles/behavior carry real regression risk
  for uncertain benefit; documented instead so they're not forgotten.

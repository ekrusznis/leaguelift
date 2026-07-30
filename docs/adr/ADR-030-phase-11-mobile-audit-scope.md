# ADR-030: Phase 11 Mobile/Responsive Audit — Scope, Method, and Gate

## Status
Accepted

## Context

DESIGN-DOC.md section 14 (Phase 11 roadmap row) frames Phase 11 as "post-pilot platform
expansion: a mobile app, and standalone player/event registration workflows... both
require pilot evidence before starting per §18.4." No real pilot (real organizations
using the platform) exists today. The founder's 2026-07-29 revision to this row already
carved out one ungated piece: a responsive/mobile-web audit of every existing page,
since section 11.2 claims public pages are "mobile-first" but this has never been
verified.

Three scope questions were resolved with the founder before starting:

1. **The pilot gate stays in force.** This phase is audit-and-fix only. The
   WebView-vs-native app-shell decision and standalone registration workflows remain
   explicitly deferred, not attempted here — the roadmap's own stated gate isn't being
   worked around just because there's no formal pilot process in a solo/agent-driven
   build.
2. **Audit method is code-level, not live-viewport screenshots.** A `resize_window`
   call against the Chrome browser tool reports success, but a direct
   `window.innerWidth` check immediately after (on `/landing-preview`, requesting
   390×844) still read `1920` — confirmed via `javascript_tool`, not just a visual
   screenshot glance. This is an environment/tooling constraint on the sandboxed
   browser, not something a different click sequence or a retry fixes (the same
   limitation was hit and abandoned earlier this session, on the same page, before this
   phase started). Every finding in this phase is therefore verified by reading markup/
   Tailwind classes, not by a real rendered mobile viewport.
3. **Public-facing pages are audited first.** Marketing/landing pages, the public org/
   team/tournament page, and public checkout flows (campaign contribution, sponsorship,
   apparel store) are the highest-risk audience for a broken mobile layout — an
   unauthenticated visitor on a phone, often arriving from a shared link or QR code
   (sponsorship/campaign sharing already exists, per ADR-019). Authenticated dashboards
   (used more often on desktop, by staff/parents already logged in) are audited after.

## Decision

**Slices, in priority order:**
1. Public/marketing pages + public checkout flows (Home, Solutions overview/detail, How
   It Works, Pricing, About, Contact, Book a Demo, Talk to Sales, Help, Security, legal
   pages, the landing-preview page, `PublicPageView`, `PublicCampaignView`,
   `PublicSponsorshipView`, `PublicStoreView`).
2. Auth pages (Sign In, Register, Forgot/Reset Password, Invitation, Auth Error).
3. Dashboards (all six role dashboards plus shared dashboard widgets/registries).
4. Remaining app pages (Organizations list, Organization detail, Household detail).

**"Properly tuned for mobile" checklist applied to every page:**
- No horizontal overflow or fixed-pixel-width element that breaks at narrow widths.
- Navigation has a working narrow-width fallback, not a desktop-only menu that simply
  disappears.
- Tap targets are reasonably sized for touch, not sized only for a mouse pointer.
- Text remains readable without depending on desktop-width line lengths.
- Images/media scale (the `w-full h-auto` pattern already used correctly elsewhere in
  this codebase — see `IcsCalendarProvider`'s sibling frontend work on the
  `landing_page_vis*.png` swap), not fixed pixel dimensions.
- Wide content (tables, code blocks, wide diagrams) scrolls within its own container
  rather than forcing the whole page to scroll horizontally.
- Forms and checkout flows are usable one-handed on a phone-width screen.

**Only real, found issues get fixed** — this is a verification-and-repair pass against
already-shipped pages, not a redesign or a new component library.

## Consequences

- No backend changes this phase — this is entirely `frontend/` markup/CSS work.
- Because verification is code-level, a genuine rendering bug that only manifests at
  runtime (e.g., a browser-specific flex/grid quirk, a JS-computed inline style) could
  be missed. This is an accepted limitation of the sandboxed browser tool, not a gap in
  audit effort — a real device/BrowserStack-style check remains a manual follow-up the
  founder can do outside this session.
- The WebView-vs-native decision and standalone registration workflows remain fully
  unscoped and unbuilt after this phase — revisit only with real pilot usage evidence,
  per the roadmap's existing gate.

## Alternatives Considered

- **Overriding the pilot gate to also plan/prototype a WebView shell or registration
  forms this phase**: rejected per the founder's decision — the roadmap's gate is
  deliberate, not an oversight, and there's no pilot feedback yet to design either
  feature against.
- **Retrying `resize_window` with different parameters/timing, or attempting a
  DevTools-protocol-level device-emulation workaround**: not attempted beyond the
  `javascript_tool` confirmation check — the tool had already failed identically once
  earlier this session on the same page; a third attempt without a different mechanism
  wouldn't produce a different result, per this session's own guidance to stop and ask
  rather than loop on a failing tool.
- **Auditing every page in one undifferentiated pass**: rejected — prioritizing by
  real-world mobile-visitor risk (public/unauthenticated first) surfaces the
  highest-value fixes earliest even if the later slices take longer to reach.

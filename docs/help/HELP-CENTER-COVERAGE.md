# LeagueLift Help Center coverage standard and inventory

**Status:** Active cross-phase requirement
**Established:** 2026-08-01

The Help Center is a maintained product surface. A feature is not documentation-complete merely because its API and UI exist.

## Shipping rule for every feature slice

Every major feature slice must do one of the following before it is marked complete:

1. Add at least one accurate published help article for each materially new user workflow; or
2. Update an existing article when the workflow, permission boundary, status model, or terminology changes; or
3. Record a concrete article gap in this inventory with an owner and planned completion phase.

Articles must be audience-scoped (`PUBLIC`, `OWNER_ADMIN`, `COACH`, `GUARDIAN`, `ATHLETE`, or `PLATFORM`), use the labels and routes that exist in the product, and avoid claiming provider behavior, response times, or permissions that have not been implemented.

## Pre-pilot completeness gate

Before the Phase 21 production go/no-go, LeagueLift must review every reachable route and major action for every persona. The review must verify:

- a user can find the relevant article from `/help` or `/app/help`;
- setup, normal use, failure recovery, and permission limitations are explained;
- screenshots or walkthrough media, when added, contain no real customer or youth data;
- links and button labels match the current application;
- provider-dependent behavior is clearly separated from local/manual behavior;
- obsolete articles are updated or archived rather than left searchable.

## Current feature inventory

| Product area | Primary audiences | Required article coverage | Current state |
|---|---|---|---|
| Owner registration, email verification, sign-in, password recovery | Public, Owner/Admin | Registration, verification resend, reset, common access failures | Starter coverage; detailed walkthroughs pending |
| Invitations and account activation | Public, Owner/Admin, Coach, Guardian, Athlete | Send/resend, accept, expired/used links, existing-account claim | Starter coverage; detailed role articles pending |
| Organization profile, branding, public pages, settings | Owner/Admin | Initial setup, logo/cover, publishing, archiving, visibility | Checklist exists; detailed articles pending |
| Teams, tournaments, season rollover | Owner/Admin, Coach | Create/manage, roster access, branding, rollover preview/copy/archive boundaries | Detailed articles pending |
| Households, guardians, participants, profile corrections | Owner/Admin, Guardian, Athlete | Creation/import, linked access, correction request/review, profile photos | Guardian basics exists; **household media center article (photo/video upload, release-publicly) published in V86**; remaining detailed articles pending |
| Manual onboarding and bulk actions | Owner/Admin | CSV format, preview errors, deduplication, execution, bulk invitations/assignments | Article pending |
| Events, RSVP, templates, calendars, maps | Owner/Admin, Coach, Guardian, Athlete | Create/publish, template use, RSVP, reminders, ICS/directions, status recovery | Overview exists; **source-update review/apply article published in V86** covering the manual-pull redesign (ICS/CSV sources no longer auto-overwrite); remaining detailed articles pending |
| Fees, payments, documents, acknowledgements | Owner/Admin, Guardian | Templates, assignments, balances, payment plans/installments, offline/online payments, reminders, acknowledgements | **Payment-plan article published in V39**; remaining detailed articles pending |
| Fundraising campaigns and contributions | Owner/Admin, Coach, Public | Campaign lifecycle, launch reminders, public contribution, refund boundary | Detailed articles pending |
| Stores, manual vendors/products, orders, fulfillment, reprints | Owner/Admin, Public, Guardian/Athlete where allowed | Catalog setup, checkout, tracking, exception handling, reprints | **Reordering article (including the vendor-no-longer-carries-this-item dialog) published in V86**; remaining detailed articles pending |
| Sponsorship packages, sponsor review, directory, renewals | Owner/Admin, Public | Package setup, purchase, approval/rejection, logo/directory, reminders | Detailed articles pending |
| Offline financial records and corrections | Owner/Admin | Record, pending verification, verify, acknowledgement, correction preview, refund/reversal, ledger/payout boundary | **Offline-record article published in V38 and correction article published in V39** |
| Reports, collections, payout summaries, reconciliation, exports | Owner/Admin, Finance/Viewer | Reading totals, export meaning, payout eligibility, running reconciliation, discrepancy handling | **Reconciliation article published in V39**; remaining reporting articles pending |
| Action Center, announcements, notification preferences | All authenticated roles | Task sources, read state, publishing, audiences, email/SMS preferences | Article pending |
| Help Center and support cases | Public, all authenticated roles, Platform | Search, submit case, case statuses, no live chat/SLA | Starter UI copy exists; article pending |
| Platform Admin console and support access | Platform | Directories, scoped support sessions, audit, queues, article/case administration | Starter article exists; detailed articles pending. **V86**: article authoring now supports image/GIF/video/PDF attachments and `![alt](url)` image/video embeds in article bodies (SupportMarkdown), in addition to the existing text/link/list Markdown subset |
| Personal and organization integrations | All authenticated roles, Owner/Admin | Personal vs. organization ownership, connection states, ICS fallback, provider-specific limits, reauthorization/disconnect | **Connection-status article published in V40; Google Calendar and platform-managed-provider articles published in V41; QuickBooks, SportsEngine, GameChanger/MaxPreps, and sync-history articles published in V42; the QuickBooks owner/admin article is forward-corrected for Rally26 Phase 29 mapping/readiness boundaries in V72** |
| Platform provider readiness | Platform | Sanitized configuration checks, difference between readiness and live health, failure recovery | **Platform-managed-provider article published in V41 and sync-history/provider-readiness guidance published in V42**; provider-specific live recovery steps remain Phase 20 evidence-backed content |
| Privacy, security, accessibility, legal pages | Public, all roles | Youth-data boundaries, security expectations, accessibility/support paths | Privacy starter exists; legal review remains a launch gate |

## Content-production sequence

1. Treat every Phase 19–22 implementation slice as documentation-bearing work and add or update the affected role-specific articles before the slice is considered complete.
2. During Phase 19–20, document each integration only after its real contract and activation behavior are verified.
3. Before Phase 21 go/no-go, run the complete route/persona audit and close all “pending” rows above.
4. During Phase 22, update articles from pilot support cases and observed user confusion; do not add speculative content unsupported by real workflows.

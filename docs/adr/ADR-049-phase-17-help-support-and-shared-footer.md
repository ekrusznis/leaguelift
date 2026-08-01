# ADR-049: Phase 17 Help Center, support intake, and shared footer foundation

**Status:** Accepted and implemented locally
**Date:** 2026-08-01

## Context

Phase 17 was originally sequenced as Action Center, announcements, reminder expansion, Help Center, then support cases. The founder requested that the Help Center/support experience be built first and that every user-facing surface gain a consistent footer with legal, support, brand, copyright, and real configured social links.

LeagueLift already has an outbox worker and `EmailProvider`, but production LeagueLift-domain email remains a Phase 20 activation task. The first support release must therefore make the database record authoritative and email a retryable consequence, not the persistence boundary.

## Decision

1. Reorder Phase 17 implementation without changing its total scope:
   - **17.1:** Help Center, durable support intake, Platform Admin authoring/queue, and shared footer foundation.
   - **17.2:** Action Center.
   - **17.3:** one-way announcements.
   - **17.4:** reminder and delivery expansion.
2. Add `support_article` with a draft/published/archived lifecycle, ordered categories, slugs, and explicit audiences: `PUBLIC`, `OWNER_ADMIN`, `COACH`, `GUARDIAN`, `ATHLETE`, and `PLATFORM`.
3. Public reads return only published `PUBLIC` articles. Authenticated reads derive allowed audiences from real authorization contexts and always include `PUBLIC`; organization roles cannot publish platform-wide content.
4. Add `support_case` as a durable, idempotent ticket record. Public intake stores only the adult requester name/email plus minimum issue details. Authenticated intake binds the current user and verifies optional organization membership.
5. Insert the case, one `support.case.created` outbox event, and its audit record in the same transaction. Provider failure never removes the case; the existing failed/dead-letter tooling remains the retry path.
6. Platform Admin capabilities separately protect help-article management and support-case triage. Queue changes are typed, transition-checked, assignment-limited to active Platform Admin users, resolution-required for resolved/closed states, and audited.
7. The confirmation email uses the existing `EmailProvider`, with configured `cc`, `reply_to`, and provider idempotency. The support mailbox address is non-secret runtime configuration; domain/mailbox activation remains Phase 20.
8. Add public `/help` and authenticated `/app/help`, article routes, support forms, caller-owned case history, and Platform Admin Help Articles and Support Cases pages.
9. Add a footer to marketing, authentication, routed app, dashboard, and published public-page layouts. Social links render only when a real `VITE_SOCIAL_*_URL` is configured; no placeholder accounts are invented.
10. This release is ticket/email support. It adds no live chat, customer-agent thread, voice, or response-time/SLA promise.

## Consequences

- The Help Center is usable before every later communication feature is complete.
- Support requests survive email outages and are operationally visible.
- Public and authenticated content share one catalog while preserving audience restrictions.
- Legal/footer links are consistently reachable across personas and page classes.
- Action Center, announcements, and reminder expansion remain unbuilt Phase 17 work.
- Production delivery from `support@leaguelift.io` remains blocked on Phase 20 DNS/provider/mailbox activation.

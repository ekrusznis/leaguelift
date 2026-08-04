# ADR-059: Contact Us Section, Google Workspace SMTP for Support Correspondence, and Resend Status-Change Notifications

## Status
Accepted

## Context

The landing site had a standalone "Book a Demo" page/button and a separate `/contact`
page, neither backed by any real lead-routing or support infrastructure — both were
static pages with no submission handling. Separately, Phase 17 (ADR-049) had already
built a working `support_case` backend (`SupportCaseService.createPublic`/
`createAuthenticated`, both writing to the same table and firing a
`support.case.created` outbox event) for the in-app Help Center's support-ticket flow,
but nothing on the public marketing site used it.

The founder asked to remove Book a Demo entirely, replace `/contact` with a branded
Contact Us section embedded on the homepage above the footer, and have both that
section and the authenticated in-app support-ticket form send their message content to
the real support mailbox (`support@rally26.com`) via Google Workspace SMTP rather than
Resend — with Resend reserved for a different purpose (see Decision). This meant
introducing a second email transport alongside the existing Resend-backed
`EmailProvider` (ADR-022/058) for the first time.

## Decision

**Trigger-type split between SMTP and Resend, not a full transport switch.** Google
Workspace SMTP is used only for the human-authored correspondence going *into* support
— the anonymous contact-us submission and the authenticated support-ticket creation,
both landing on the same `support_case` table. This is functionally a receipt/forward
of the person's own written message to a real mailbox, not a branded lifecycle
notification, so a plain-text SMTP send (no hosted template) is the right shape.
Resend stays reserved for automated lifecycle/status-change notifications — the four
ADR-058 emails, plus a new one added here: `support.case.status_changed`, sent back to
the requester when platform staff change a case's status. This was the founder's
explicit correction during scoping (the original ask conflated "use SMTP for support
emails" broadly; the clarified split is SMTP in, Resend out).

**Contact Us reuses `support_case`, not a new table.** The landing page's Contact Us
form (`ContactUsSection.tsx`, rendered on `HomePage` just above `SiteFooter`) submits
through the same unauthenticated `SupportCaseService.createPublic` endpoint the Help
Center already used, with `category = OTHER` and a fixed subject
("Website contact form inquiry"). This means every contact-us inquiry is already a
first-class, trackable, auditable support case with no new backend model — the
tradeoff is that it inherits `support_case`'s existing shape (a required
name/email/subject/description, an idempotency key, a honeypot-style optional
organization field folded into the description) rather than a purpose-built
lead-capture schema.

**Book a Demo removed outright; `/contact` becomes a redirect, not a page.**
`BookDemoPage.tsx` and `ContactPage.tsx` are deleted. `/book-demo` now redirects to
`/talk-to-sales` (the existing sales-inquiry path). `/contact` renders a new
`ContactRedirect` component that navigates to `/` with
`state: { scrollTo: HOMEPAGE_SECTION_IDS.contactUs }`, reusing the pre-existing
`useScrollToHash`/`usePendingHomeScroll` state-handoff pattern (not a literal URL hash)
so old bookmarks/links to `/contact` still land on the right section instead of 404ing
or silently losing the destination.

**`SmtpEmailProvider` is a new, separate component — deliberately not an
`EmailProvider`.** It has the same-shaped `send(message: EmailMessage)` method as the
existing `EmailProvider` interface, but does not implement it, specifically so it never
becomes a second candidate bean at the many existing unqualified `EmailProvider`
injection points across the codebase (which would break startup with an ambiguous-bean
error). Callers that want SMTP — currently only `SupportCaseCreatedEmailHandler` —
inject the concrete `SmtpEmailProvider` class directly. It wraps a lazily-built
`JavaMailSenderImpl` (`smtp.gmail.com:587`, STARTTLS, standard app-password auth, per
the founder's choice over OAuth2/XOAUTH2) configured from a new
`SmtpMailProperties` (`rally26.email.smtp.*`); a blank username no-ops (logs and
returns) exactly like `LoggingEmailProvider` does for Resend, so this is safe to leave
unconfigured in any environment.

**Mustache templating for the SMTP plain-text body.** A new
`MustacheTemplateRenderer` (`DefaultMustacheFactory`, classpath-resolving) renders
`mail-templates/support-case-created.mustache` with the case's requester name, case id,
category, subject, and description. Triple-mustache (`{{{var}}}`) is used throughout —
the opposite reason from ADR-058's Resend HTML templates' use of the same syntax: here
it's because this is a *plain-text* email and double-mustache's default HTML-escaping
would incorrectly inject literal `&amp;`/`&lt;` into a message a human is going to
read as text, not markup.

**New Resend-templated notification: `support.case.status_changed`.**
`SupportCaseService.updatePlatform` now compares the case's status before and after the
update and, only on a real transition (not a no-op re-save or a priority-only change),
inserts a new outbox event. `SupportCaseStatusChangedEmailHandler` (mirroring the
existing `PasswordResetEmailHandler` pattern) sends via the primary Resend
`EmailProvider`, with a `supportCaseUpdateId` template ID (new
`ResendTemplateProperties` field, blank-default) carrying `CASE_SUBJECT`,
`STATUS_LABEL`, `RESOLUTION_HTML`, and `CASE_URL` variables, and a populated
plain-text fallback body when no template ID is configured — the same
blank-ID-falls-back-safely convention ADR-058 established.

## Consequences

- Two email transports now exist side by side (Resend for branded automated
  notifications, SMTP for plain-text human correspondence), each reachable from a
  different, non-overlapping set of call sites. A future notification should be
  triaged against this same question — "is this a lifecycle/status event, or a
  forward of something a person wrote" — to decide which one it uses, rather than
  defaulting to whichever provider is already injected nearby.
- `support_case` is now the single backend model for three previously-separate
  surfaces (in-app Help Center tickets, landing-page contact-us, and — going
  forward — anything else that wants "send a message to support") rather than a
  purpose-built contact-form/lead table. This keeps the founder-facing support queue
  unified at the cost of the contact-us form inheriting some ticket-shaped fields
  (e.g. a `category` enum) that don't perfectly map to a pre-sales inquiry.
- A real Google Workspace App Password (`SMTP_PASSWORD`) must still be generated and
  set as a GitHub Actions secret by the founder — the assistant cannot generate real
  credentials. Until then, `SmtpEmailProvider` safely no-ops (logs only), matching how
  `RESEND_API_KEY` was handled before it was first configured.
- The new `support-case-update` Resend template has not yet been designed/created in
  Resend's dashboard (unlike the four ADR-058 templates, which were built and visually
  verified against a real inbox). The handler is safe to ship ahead of that — it falls
  back to plain text — but the branded version is a follow-up, not part of this ADR's
  delivered scope.
- All frontend changes were verified via `npm run typecheck` (clean). Backend changes
  (SMTP provider, Mustache renderer, the new outbox wiring, and their unit tests) were
  reviewed by hand for import/signature correctness but not machine-compiled or
  test-run — this sandbox still has no network access to fetch the Gradle wrapper
  distribution, the same pre-existing limitation ADR-058 documented. Treat as
  reviewed-but-not-CI-confirmed until the next real CI run.

## Alternatives Considered

- **Route contact-us and support-ticket emails through Resend too, for one unified
  transport.** Rejected per the founder's explicit correction: Resend's hosted
  templates are built for consistent branded lifecycle copy, not for forwarding
  arbitrary free-text messages a person just typed — SMTP's plain-text send is a
  better fit for that content, and keeping the two transports scoped by trigger type
  (automated vs. human-authored) is a clearer rule than picking per call site.
- **Build a dedicated `contact_lead` table instead of reusing `support_case`.** Would
  give the contact-us form a purpose-built shape (no `category` enum, no ticket
  status lifecycle to reason about for a pre-sales inquiry). Rejected as the founder's
  explicit "Reuse support_case" answer during scoping — a second parallel table would
  fragment the founder's single support queue for marginal shape benefit.
- **Full Resend replacement for support-case creation emails too** (offered as an
  option during scoping). Rejected by the founder's clarifying counter-instruction —
  see Decision's trigger-type split.

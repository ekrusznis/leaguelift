# ADR-058: Resend Hosted Email Templates, Welcome Email, and an Auth/UX Hardening Pass

## Status
Accepted

## Context

Phase 8 (ADR-022/023/024) already shipped a real Resend-backed `EmailProvider` and
wired verify-email, password-reset, and invitation notifications through it — but
those three emails were still composed as inline HTML strings built in Kotlin. Resend
also offers a hosted, WYSIWYG **Templates** feature (design in Resend's dashboard,
reference by template ID, pass runtime values as variables) that keeps visual design
out of application code and lets a non-engineer iterate on copy/branding directly.
Separately, four real user-facing issues surfaced while testing these flows
end-to-end against a real inbox in production:

1. A verification link clicked once (and which had genuinely already succeeded
   server-side, evidenced by the welcome email having gone out) could still render as
   "invalid or expired" — an alarming, misleading failure state for what was actually
   a success.
2. `InvitationPage` told an unauthenticated invitee to "sign in first" with no way to
   tell the page they didn't have an account yet — there was no register path wired
   in at all for that case.
3. `ResetPasswordPage`, `ForgotPasswordPage`, and `ResendVerificationPage` rendered
   their form labels in `text-navy-900` (near-black) on the same dark `bg-navy-800`
   card every other auth page uses — functionally invisible text, because those three
   pages were the only ones that hadn't been passing `tone="dark"` to the shared
   `FormField`/`PasswordField` primitives.
4. Client-side navigation to a `/solutions/:slug` page could land the viewport
   scrolled to wherever the previous page had been scrolled to, instead of the top —
   React Router doesn't reset scroll position on `<Link>` navigation the way a full
   page load does.

## Decision

**Resend hosted templates (four templates, IDs configured per environment):**
`verify-email`, `password-reset`, `rally-invitation`, and a new `welcome-email` were
built in Resend's dashboard template editor, matching the existing brand system
(§3 color tokens — navy-950 header, orange-500 CTA). `EmailMessage` gained an optional
`template: EmailTemplateRef?` (`id` + `variables: Map<String, Any>`); when set,
`ResendEmailProvider` sends only `template`, omitting `subject`/`text` (Resend treats
these as mutually exclusive). Template IDs are bound from four new env vars
(`RESEND_TEMPLATE_VERIFY_EMAIL_ID` / `_PASSWORD_RESET_ID` / `_INVITATION_ID` /
`_WELCOME_ID`, all blank-default) via a new `ResendTemplateProperties`
`@ConfigurationProperties` bean — a blank ID falls back to the prior inline-HTML path
unchanged, so this is additive, not a breaking migration. Resend templates only accept
scalar variables (no loops/arrays), which shapes the welcome email below.

**Welcome email — new trigger.** `MembershipService.grantOwner`/`grantMembership` now
check whether the grantee had *any* prior active membership before inserting the new
one; on a genuine first-ever membership, a new `membership.first_granted` outbox event
fires. `WelcomeEmailHandler` re-fetches membership/user/org fresh (skip-if-gone, the
established outbox-handler pattern) and sends `ORG_NAME`, `ROLE_LABEL`,
`DASHBOARD_URL`, and `FEATURES_HTML` — a pre-rendered block of `<tr>` row HTML chosen
per role by a new `WelcomeEmailFeatures` object, working around Resend templates
having no loop support by doing the "loop" in Kotlin instead of the template.

**Email-verification idempotency.** `EmailVerificationService.verify()` previously
threw the same `ValidationException`/400 for "token never existed," "already used,"
and "expired" — indistinguishable to the frontend, which showed one generic error for
all three regardless of cause. An already-used token now throws a distinct
`ConflictException("EMAIL_VERIFICATION_ALREADY_USED", ...)`. This also closes a real
race: `consume()`'s row-count return value (previously discarded) is now checked —
if two requests both pass the `consumedAt == null` read before either commits, the
loser's `UPDATE` affects zero rows and now correctly throws the same conflict instead
of silently reporting success. `VerifyEmailPage` special-cases this code as a
*success* state (an already-used link almost always means this exact account already
verified — an earlier click, or a slow request that actually succeeded), and a
`useRef` in-flight guard stops a fast double-click from firing two requests before
the button's `disabled` state re-renders.

**Invitation acceptance choice.** For an unauthenticated visitor, `InvitationPage` now
shows both **Sign In** and **Create Account** — not just a single "sign in first"
message with no register path. Both carry a `next=/auth/invitation?token=...`
redirect: `SignInPage` already honored `next` (unchanged); `RegisterPage` now reads
its own `next` param and forwards it into the post-verification "Go to Sign In" link,
so accepting resumes automatically once the person is authenticated, regardless of
which path they took. Registering here reuses the existing `/auth/register` endpoint
unmodified — despite its "owner" naming, it only ever creates a bare `AppUser` pending
verification and never creates an organization (organization creation is a separate,
later in-app step), so it was already generically correct for an invited non-owner
role; no backend change was needed for this part.

**Dark-background label contrast.** `ResetPasswordPage`, `ForgotPasswordPage`, and
`ResendVerificationPage` now pass `tone="dark"` to their `PasswordField`/`FormField`
instances, matching `RegisterPage`/`SignInPage`, which already did.

**Scroll restoration.** A new `ScrollToTop` component (`useLocation()` +
`useEffect(() => window.scrollTo(0, 0), [pathname])`) renders once inside
`<BrowserRouter>` in `App.tsx`, above `<AppRoutes>`.

**Solution-page hero images (unrelated to the above, bundled in the same working
session):** five semi-transparent illustrated background images
(`frontend/public/demo-assets/solutions/`, WebP, ~220-410KB each) were added as
full-bleed hero backgrounds behind a legibility gradient scrim on five of the six
Solutions pages (Team & Tournament Pages, Fundraising, Dues & Fees, Family Credits,
Sponsorships — Apparel keeps its existing foreground product photo instead), via a new
`solutionHeroImages.ts` asset registry and `SolutionContent.heroBackground`.

## Consequences

- Visual copy/branding for these four emails can now be iterated in Resend's
  dashboard without a code deploy, at the cost of that branding no longer being
  version-controlled in this repository — the four template IDs are, but their
  content lives in Resend, matching the tradeoff every hosted-template system makes.
- `EMAIL_VERIFICATION_ALREADY_USED` is a new, additive error code; nothing previously
  depended on "already used" being folded into `VALIDATION_FAILED`.
- An invited person who chooses "Create Account" still goes through the full
  email-verification round trip before they can sign in and accept — this is the
  existing registration flow's behavior, not a new delay introduced here. `next` is
  not carried through the verification email link itself (that link is generated
  server-side from a fixed base URL, and typically opened in a different tab/session
  than the one that registered), so a person who verifies via email and clicks the
  verify page's own "Sign In" button (rather than navigating back to their original
  invitation email) lands on the generic post-sign-in destination, not back on the
  invitation page. They still have the invitation email itself to return to. Closing
  this fully would require threading `next` through the verification token's payload
  and email template — deferred as a minor, easily-worked-around gap, not built here.
- The four email templates were sent to a real inbox and visually verified this
  session; the invitation/register/reset-password UX changes were verified by
  `npm run typecheck` and manual code review only — this sandbox cannot run the
  Gradle backend test suite (no network access to `services.gradle.org` to fetch the
  wrapper distribution) or the frontend's `oxlint`-based `npm run lint` (missing
  native binding for this sandbox's architecture), both pre-existing sandbox
  limitations unrelated to this change. New/updated Kotlin tests
  (`EmailVerificationServiceTest`) are written but not machine-verified before this
  ADR was recorded; treat as reviewed-but-not-CI-confirmed until the next real CI run.

## Alternatives Considered

- **Auto-verify email on page load instead of requiring a button click.** Would have
  sidestepped the double-click race entirely. Rejected — the manual click is
  deliberate protection against corporate email-security scanners
  (Microsoft Safe Links, Proofpoint, etc.) that pre-fetch/pre-render links in email
  bodies to scan them, which would silently consume a single-use verify-on-load token
  before the real recipient ever saw the message. Fixing the idempotency handling
  instead preserves that protection.
- **Give `InvitationPage` a public "preview this invitation" endpoint** (show the
  inviting org/role before requiring auth). Would be a better experience but requires
  a new unauthenticated backend endpoint and a decision about what's safe to expose
  pre-auth; out of scope for this pass. The existing documented limitation (no public
  preview-by-token endpoint) is unchanged by this ADR.
- **Thread `next` through the verification email itself** so the sign-in-after-verify
  loop always closes back onto the invitation page. Rejected for this pass as
  disproportionate scope (touches the token payload, the email template variables,
  and the verify endpoint) relative to the gap it closes — see Consequences above.

# ADR-024: Phase 8 Slice 3 — One-Way SMS via Twilio

## Status
Accepted

## Context

DESIGN-DOC.md's Phase 8 roadmap row scopes the final piece as "one-way SMS
via Twilio for payment/fundraising reminders." ADR-022 (slice 1) built the
outbox worker and a real `EmailProvider`; ADR-023 (slice 2) wired fee-payment
reminders, order/contribution confirmations, and sponsorship approval/refund
notices, plus a minimal `household.email_reminders_opt_out` flag, and
explicitly deferred "campaign launch emails" for lack of a subscriber model.
This slice adds SMS as a second channel — but only where a concrete trigger
already exists: fee-payment reminders (built in slice 2). There is no
concretely-built "fundraising reminder" trigger to attach SMS to yet (the
same subscriber-model gap ADR-023 already identified for campaign-launch
emails), so fundraising SMS stays deferred alongside it, not invented here.

`household.contact_phone` has existed since Phase 2 (V5) — there is already
a phone number to send to. What's missing is consent: sending unsolicited
SMS carries real per-message cost and much stricter legal/consent norms than
email (TCPA-style expectations in the US), so this needed its own explicit
decision before building anything, per DESIGN-DOC.md section 19.3's "do not
guess" instruction.

## Decision

**1. SMS requires explicit opt-IN, not opt-out (the reverse of email).**
`household` gains `sms_reminders_opt_in boolean not null default false` (V21)
— defaulting to `false` for every existing and new household, unlike
`email_reminders_opt_out` (V20) which defaults `false` meaning email
reminders are *on* by default. No household receives an SMS reminder until
an org admin explicitly flips this on for them via the existing
household-update endpoint (`UpdateHouseholdRequest.smsRemindersOptIn`,
same partial-update/coalesce pattern as every other household field).

**2. `SmsProvider`/`SmsMessage` mirrors `EmailProvider`/`EmailMessage`
exactly, with the same logging-default/real-provider split.**
`notification/SmsProvider.kt` defines the interface;
`notification/LoggingSmsProvider.kt` is the default
(`@ConditionalOnProperty(prefix = "rally26.sms", name = ["provider"],
havingValue = "logging", matchIfMissing = true)`, logging only the
destination number, never the body);
`notification/infra/TwilioSmsProvider.kt` is the real send path
(`@ConditionalOnProperty(..., havingValue = "twilio")`), active only when
`rally26.sms.provider = twilio`.

**3. Twilio's plain REST API, not the official Twilio Java SDK.** Mirrors
`ResendConfig`/`PrintifyConfig`'s no-SDK precedent —
`TwilioConfig.twilioRestClient()` builds a `RestClient` with Basic Auth
(`accountSid`:`authToken`) against
`https://api.twilio.com/2010-04-01/Accounts/{accountSid}`;
`TwilioSmsProvider.send` POSTs form-urlencoded `To`/`From`/`Body` to
`/Messages.json`. Twilio's REST API has been stable since 2010 — this
codebase only ever needs the one send endpoint, so a plain authenticated
client avoids a new SDK dependency (and its own transitive-dependency/
version-pinning risk) for one call, consistent with why Printify and Resend
were both built the same way rather than pulling in their SDKs (where they
even have one).

**4. `TwilioProperties` keeps blank Kotlin defaults in every profile
(including staging/prod), same rationale as `ResendProperties`.** Since
`"logging"` is `rally26.sms.provider`'s legitimate default everywhere,
requiring `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN`/`TWILIO_FROM_NUMBER` to be
set for startup to succeed would be requiring credentials nobody's using
yet. A blank account SID/token still produces a working client; Twilio's own
401 becomes a clean `ServiceUnavailableException` at call time, not a
startup failure.

**5. Fee-payment reminders become the first two-channel notification —
email and SMS are independent, not either/or.** `FeeRepository.findNeedingPaymentReminder`
now also selects `household.contact_phone`/`sms_reminders_opt_in`, and
resolves each channel to null-or-present *before* the payload is built: the
household's email is nulled out if `email_reminders_opt_out`, and the phone
is nulled out unless `sms_reminders_opt_in` — so `FeePaymentReminderHandler`
only ever needs a plain null check per channel, never a separate boolean.
The WHERE clause's household filter changed from "not opted out of email"
to "at least one channel is available" (`email_reminders_opt_out = false or
sms_reminders_opt_in = true`) — a household that opted out of email but
opted into SMS (or the reverse) must still surface as a candidate.
`FeePaymentReminderHandler` sends to whichever channel(s) resolved non-null;
a candidate with neither is logged and skipped, same posture as the
existing no-contact-email case.

**6. Fundraising SMS reminders: deferred, matching ADR-023's campaign-launch-email
deferral.** The Phase 8 roadmap line names "payment/fundraising reminders"
together, but this codebase has no fundraising-reminder trigger to attach
SMS (or email) to — contributions are one-time, not recurring pledges, and
"remind supporters a campaign is ending" has the exact same undefined-
subscriber-list problem ADR-023 already declined to invent a stand-in for.
SMS is wired to the one recurring reminder that concretely exists today
(fee payments); a future fundraising-reminder trigger, once someone defines
what a campaign "subscriber" is, can reuse the same `SmsProvider`/opt-in
column with no new infrastructure.

## Consequences

- `household` now carries two independent reminder-channel preferences
  (`email_reminders_opt_out`, `sms_reminders_opt_in`) with opposite
  defaults, reflecting that email-on-by-default and SMS-off-by-default are
  both deliberate, different decisions — not an inconsistency to reconcile.
- `FeePaymentReminderHandler` gained an `SmsProvider` dependency; its test
  coverage now exercises all four channel-availability combinations
  (email-only, SMS-only, both, neither).
- No SMS delivery-status/bounce tracking exists — Twilio's own delivery
  webhooks are not consumed. A failed send still surfaces through the
  outbox's own retry/backoff/dead-letter machinery (a `RestClientException`
  from `TwilioSmsProvider` propagates up through
  `FeePaymentReminderHandler.handle` exactly like an `EmailProvider` failure
  would), which is the same level of operational visibility email already
  has post-slice-1 — not a new gap SMS introduces.
- Twilio's own per-message cost is not tracked or rate-limited anywhere in
  this codebase. Acceptable while `rally26.sms.provider` stays `"logging"`
  everywhere (no real messages can be sent); revisit before ever flipping a
  real environment to `"twilio"`.
- Fundraising SMS/email reminders and campaign-launch emails both remain
  unbuilt, blocked on the same open product question (what is a campaign
  "subscriber"). A future ADR should resolve that once, not per-channel.
- This completes DESIGN-DOC.md's Phase 8 roadmap scope (outbox worker,
  real `EmailProvider`, one-way SMS via Twilio) — see the roadmap-row update
  alongside this ADR.

## Alternatives Considered

- **Defaulting `sms_reminders_opt_in` to `true` (opt-out, matching email)**:
  rejected — SMS's per-message cost and stricter consent expectations
  (TCPA-style norms) make opt-out the wrong default; an org admin must
  affirmatively turn it on per household.
- **The official Twilio Java SDK instead of a plain REST client**: rejected
  for the same reason Printify/Resend both avoided their own SDKs — one
  endpoint doesn't justify a new dependency, and Twilio's REST API is stable
  enough that wrapping it directly carries little risk.
- **A single combined "reminders enabled" boolean instead of separate
  email-opt-out/SMS-opt-in columns**: rejected — the two channels have
  opposite defaults and genuinely independent consent bases; collapsing them
  would either force both channels to share one on/off switch (impossible
  given the opposite-default requirement) or need a third "which channels"
  enum that's no simpler than two booleans.
- **Building a fundraising-reminder trigger now so Twilio's SMS wiring can
  cover "fundraising reminders" literally, per the roadmap wording**:
  rejected — same reasoning as ADR-023's campaign-launch-email deferral;
  inventing a subscriber concept to satisfy roadmap wording literally would
  be a real, independent feature decision the founder hasn't made.
- **Twilio delivery-status webhook consumption in this slice**: rejected as
  scope beyond what "one-way SMS... for payment/fundraising reminders"
  asks for; the outbox's existing retry/dead-letter visibility is
  sufficient operational coverage for this slice, matching email's own
  current lack of bounce-tracking.

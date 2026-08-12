# PCI DSS Compliance Scope

DESIGN-DOC.md §14.6 item #8. This documents Rally26's PCI DSS scope determination given how card payments are actually architected today, verified against the real code (not assumed) as of 2026-08-12. It is engineering-level documentation to support a compliance filing — it is **not** a substitute for the actual self-assessment/attestation, which is a separate action item (see "What's still required" below).

## Payment architecture — every flow, verified

Rally26 has six places money moves via Stripe. All six use the identical model: the backend creates a Stripe Checkout Session (or Billing Portal Session) server-to-server via the Stripe SDK, and the browser is **fully redirected** to a page hosted entirely on Stripe's own domain (`checkout.stripe.com` / Stripe's Billing Portal). Card entry happens exclusively on that Stripe-hosted page. Stripe redirects the browser back to a Rally26 `successUrl`/`cancelUrl` that carries no payment data, and payment confirmation happens separately, server-to-server, via a signature-verified webhook (`StripeWebhookController`).

| Flow | Backend client | Mode | Frontend redirect |
|---|---|---|---|
| Campaign contributions | `StripeCheckoutClient` | Checkout, one-time payment | `window.location.href` |
| Store orders (Swag Shop) | `StripeOrderCheckoutClient` | Checkout, one-time payment | `window.location.href` |
| Sponsorship purchases | `StripeSponsorshipCheckoutClient` | Checkout, one-time payment | `window.location.href` |
| Online fee payments | `StripeFeePaymentCheckoutClient` | Checkout, one-time payment | `window.location.href` |
| Organization subscription (Rally26's own SaaS revenue) | `StripeSubscriptionBillingClient` | Checkout, subscription mode | `window.location.assign` |
| Payment-method updates | `StripeSubscriptionBillingClient` (Billing Portal session) | Stripe Billing Portal | redirect |

Confirmed by direct inspection, not assumption: `frontend/package.json` has **no** `@stripe/stripe-js` dependency, and no `Elements`/`CardElement`/`PaymentElement` usage exists anywhere in `frontend/src`. Rally26 has never embedded a Stripe-hosted card-entry iframe on its own pages — every flow is a full off-site redirect.

## What Rally26's systems touch vs. never touch

**Never touches, by architecture, not just policy:** primary account number (PAN), CVV, or any other raw cardholder data. There is no code path — frontend or backend — capable of receiving it. The backend's own Stripe API calls (`SessionCreateParams`, `RefundCreateParams`) only ever pass amounts, currency, descriptions, and metadata (order/contribution/sponsorship/fee-payment IDs) — never card details.

**Does touch, and this is fine under PCI DSS:** Stripe webhook payloads (stored raw in the `webhook_event` table, `StripeWebhookController.kt`) and dispute records (`payment_dispute` table, added 2026-08-12 for §14.6 item #4) can include tokenized/masked payment metadata such as card brand and last 4 digits. That's explicitly not "cardholder data" under PCI DSS — only the full PAN, CVV, and full magnetic-stripe/chip data are in scope. Storing last-4/brand for reference is standard practice and doesn't expand PCI scope.

## SAQ eligibility

This architecture — full redirect to a PCI-DSS-validated third party's own hosted payment page, with the merchant's own site never receiving or transmitting cardholder data — is the standard case for the **simplest** PCI DSS Self-Assessment Questionnaire, **SAQ A** (as opposed to SAQ A-EP, which applies when a merchant embeds the processor's payment fields within its own page via an iframe/JS SDK even though the processor still handles tokenization — a meaningfully larger set of requirements, e.g. PCI DSS 4.0's payment-page script-integrity controls, which target exactly the embedded-iframe case Rally26 doesn't have).

**This determination should be confirmed against the current, official PCI SSC SAQ A eligibility criteria at attestation time** — SAQ eligibility criteria are versioned (Rally26 should confirm against whichever PCI DSS version is current when the SAQ is actually completed) and this document is not a substitute for reading the official questionnaire's own eligibility checklist.

## What's still required (not satisfied by this document alone)

1. **Complete and submit an actual SAQ A** — self-attested, but it's a real form that needs to be filled out and signed, typically requested by or submitted to Rally26's acquiring bank or through Stripe's own compliance tooling. Writing this document doesn't complete that step.
2. **Annual re-attestation** — PCI DSS compliance is validated annually, not once.
3. **Basic website security hygiene** still applies even under SAQ A (e.g., keeping the site free of malware, standard web security practices) — covered by Rally26's general security practices, not something unique to payments.
4. **If the payment architecture ever changes** — embedding Stripe Elements/Payment Element directly on a Rally26 page, or any code path that would receive raw card data — this document's SAQ A conclusion no longer holds and the scope must be re-assessed (likely SAQ A-EP or higher).

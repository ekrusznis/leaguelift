-- Phase 6 remainder (ADR-019, DESIGN-DOC.md section 14.1): deepens the sponsorship
-- proof-of-concept (V16, ADR-018) toward the full section 13 catalog. Adds:
--   1. An org-admin approval workflow between "Stripe payment confirmed" and
--      "publicly visible on the sponsor directory" (review_status), kept as a column
--      distinct from `status` (the payment lifecycle) since the two evolve
--      independently — a sponsorship can be CONFIRMED and PENDING_REVIEW at the same
--      time, or CONFIRMED and REJECTED (which also triggers a refund, see
--      SponsorshipService.reject).
--   2. Refund support (refunded_at), mirroring V15's contribution/order pattern —
--      `status` already allowed 'REFUNDED' as a forward-compatible value (ADR-018);
--      this migration is what actually makes that value reachable.
--   3. Renewal-reminder tracking (renewal_reminder_sent_at) so the new scheduled job
--      (SponsorshipRenewalReminderService) doesn't re-email the same sponsor every run.
--   4. Sponsor-contact CRM widening (phone/company_name/notes) — still bounded, not a
--      full multi-contact-per-sponsor CRM table (see ADR-019).
--
-- Deliberately NOT part of this migration: link/QR click-through analytics (no
-- tracking table — QR codes are generated on the fly from the org's existing public
-- slug, nothing persisted) and invoices (computed from existing rows, no invoice
-- numbering sequence or dedicated table).

alter table sponsor add column phone text;
alter table sponsor add column company_name text;
alter table sponsor add column notes text;

alter table sponsorship add column review_status text not null default 'PENDING_REVIEW';
alter table sponsorship add constraint sponsorship_review_status_check
    check (review_status in ('PENDING_REVIEW', 'APPROVED', 'REJECTED'));

alter table sponsorship add column reviewed_at timestamptz;
alter table sponsorship add column reviewed_by uuid references app_user (id);
alter table sponsorship add column refunded_at timestamptz;
alter table sponsorship add column renewal_reminder_sent_at timestamptz;

create index sponsorship_review_status_idx on sponsorship (review_status);

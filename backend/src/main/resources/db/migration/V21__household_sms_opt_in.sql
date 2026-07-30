-- Phase 8 slice 3 (ADR-024): one-way SMS reminders via Twilio. Unlike email
-- (household.email_reminders_opt_out, V20 — opt-out, since email is the established
-- lower-friction channel), SMS defaults to opted OUT and requires an explicit opt-IN —
-- SMS carries real per-message cost and stricter consent norms than email.
-- household.contact_phone (V5) is reused as the SMS recipient; no new phone column
-- is needed.

alter table household add column sms_reminders_opt_in boolean not null default false;

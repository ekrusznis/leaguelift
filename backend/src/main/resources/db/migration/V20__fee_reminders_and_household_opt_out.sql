-- Phase 8 slice 2 (notification trigger expansion, following the outbox worker + real
-- EmailProvider built in slice 1/ADR-022): fee-payment reminders need a sent-marker
-- column on fee_assignment, mirroring sponsorship.renewal_reminder_sent_at (V17). A
-- household also gets a minimal email opt-out flag — the only new notification
-- "preference" this slice adds, deliberately not a full per-channel/per-notification-type
-- preferences table.

alter table fee_assignment add column payment_reminder_sent_at timestamptz;

alter table household add column email_reminders_opt_out boolean not null default false;

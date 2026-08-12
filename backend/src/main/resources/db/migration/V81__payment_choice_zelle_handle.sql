-- Phase 32 (Payment Choice Expansion) scaffold: an org-configured Zelle handle,
-- shown as payment instructions on the household payment-choice screen. Zelle
-- itself stays manual/external (FeePayment.PaymentMethod.ZELLE already exists,
-- unaffected by this migration) — this only lets an org tell guardians where to
-- send it.

alter table organization
    add column zelle_handle text;

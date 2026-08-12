-- Online fee payments: a guardian can now pay a fee_assignment directly via Stripe
-- Checkout, alongside the existing staff-recorded offline payment methods.
-- A new 'PENDING_CHECKOUT' row is inserted before Stripe returns a session id
-- (mirroring `contribution`'s own insertPending/attachStripeSession shape) and
-- flips to 'CONFIRMED' only from the webhook. sumActiveByAssignment must exclude
-- PENDING_CHECKOUT rows so an unpaid checkout attempt never affects the household's
-- outstanding balance.

alter table fee_payment
    add column status text not null default 'CONFIRMED',
    add column stripe_checkout_session_id text,
    add column stripe_payment_intent_id text,
    add column payer_email text,
    add column payer_name text;

alter table fee_payment
    add constraint fee_payment_status_check check (status in ('PENDING_CHECKOUT', 'CONFIRMED', 'CANCELED'));

alter table fee_payment
    add constraint fee_payment_stripe_checkout_session_key unique (stripe_checkout_session_id);

alter table fee_payment
    drop constraint fee_payment_method_check;
alter table fee_payment
    add constraint fee_payment_method_check check (method in ('CASH', 'CHECK', 'VENMO', 'ZELLE', 'OTHER', 'STRIPE_ONLINE'));

alter table ledger_entry
    drop constraint ledger_entry_source_type_check;
alter table ledger_entry
    add constraint ledger_entry_source_type_check
    check (source_type in ('CONTRIBUTION', 'ORDER', 'TRANSFER', 'REFUND', 'SPONSORSHIP', 'CORRECTION', 'FEE_PAYMENT'));

-- Tracks a real Stripe refund on a fee payment, distinct from the pre-existing
-- voided_at/voided_by_user_id/void_reason bookkeeping columns (which also cover a
-- plain manual void of a cash/check/etc. payment, or a staff "force void" override
-- that does not call Stripe). Non-null only when the payment was actually refunded
-- through Stripe (see FeeService.refundPayment).
alter table fee_payment
    add column stripe_refund_id text;

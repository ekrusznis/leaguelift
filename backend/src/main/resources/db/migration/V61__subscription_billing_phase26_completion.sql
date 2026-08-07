-- Phase 26 completion (ADR-080): owner billing recovery + Platform Admin visibility.
-- Stripe subscription events remain authoritative for access; this migration adds only
-- durable payment-success/cancellation observability and Help Center coverage.

alter table organization_subscription
    add column last_payment_success_at timestamptz,
    add column cancel_at_period_end boolean not null default false;

create index organization_subscription_updated_idx
    on organization_subscription (updated_at desc);

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('26000000-0000-4000-8000-000000000001', 'manage-rally26-organization-billing',
     'Manage your Rally26 organization billing',
     'View subscription status, recover a failed payment, update billing details, or cancel through Stripe Billing Portal.',
     E'Open **Billing** from your organization navigation to see the plan and current subscription state. Use **Manage billing in Stripe** to update payment details, review invoices, or manage cancellation. A browser return from Stripe never changes Rally26 access by itself; Rally26 waits for signed Stripe subscription webhooks. If billing is past due, the organization remains available for recovery until Stripe reports a definitive canceled or incomplete subscription state.',
     'Organization Setup', 'OWNER_ADMIN', 'PUBLISHED', 80, now()),
    ('26000000-0000-4000-8000-000000000002', 'platform-subscription-visibility',
     'Review organization subscription status',
     'Use Platform Admin subscription visibility to find organizations that have not started checkout or need billing recovery.',
     E'Platform Administrators can open **Subscriptions** to filter organizations by Rally26 subscription state. The view reports organization/plan/status, payment success or failure timestamps, and whether Stripe customer/subscription identifiers are linked. It is intentionally read-only: customer billing changes stay in the owner billing flow and Stripe Billing Portal, while signed Stripe webhooks remain authoritative for Rally26 access state.',
     'Platform Operations', 'PLATFORM', 'PUBLISHED', 80, now());

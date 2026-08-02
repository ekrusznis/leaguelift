-- Phase 18 slice 3: organization-recorded offline contributions, sponsorships,
-- and merchandise orders. These records explicitly identify OFFLINE payment
-- sources and never fabricate Stripe/provider identifiers. Verification creates
-- append-only ledger entries, but no payout-eligible ORGANIZATION_EARNING row:
-- LeagueLift did not receive or hold these funds.

alter table contribution add column payment_source text not null default 'STRIPE';
alter table contribution add constraint contribution_payment_source_check
    check (payment_source in ('STRIPE', 'OFFLINE'));
alter table contribution add constraint contribution_payment_source_fields_check check (
    payment_source = 'OFFLINE'
    or stripe_checkout_session_id is not null
    or status = 'PENDING'
);

alter table sponsorship add column payment_source text not null default 'STRIPE';
alter table sponsorship add constraint sponsorship_payment_source_check
    check (payment_source in ('STRIPE', 'OFFLINE'));
alter table sponsorship add constraint sponsorship_payment_source_fields_check check (
    payment_source = 'OFFLINE'
    or stripe_checkout_session_id is not null
    or status = 'PENDING'
);

alter table "order" add column payment_source text not null default 'STRIPE';
alter table "order" add constraint order_payment_source_check
    check (payment_source in ('STRIPE', 'OFFLINE'));
alter table "order" add constraint order_payment_source_fields_check check (
    payment_source = 'OFFLINE'
    or stripe_checkout_session_id is not null
    or status = 'PENDING'
);

alter table ledger_entry drop constraint ledger_entry_type_check;
alter table ledger_entry add constraint ledger_entry_type_check check (entry_type in (
    'CONTRIBUTION', 'GROSS_SALE', 'PRODUCTION_COST', 'LEAGUELIFT_PLATFORM_FEE',
    'ORGANIZATION_EARNING', 'TRANSFER', 'REFUND', 'OFFLINE_SETTLEMENT'
));

create table offline_financial_record (
    id                       uuid primary key default gen_random_uuid(),
    organization_id          uuid not null references organization (id),
    record_type              text not null,
    record_id                uuid not null,
    display_label            text not null,
    payment_method           text not null,
    verification_status      text not null default 'PENDING_VERIFICATION',
    amount_minor             bigint not null,
    currency                 text not null default 'USD',
    payer_name               text,
    payer_email              text,
    payment_reference        text,
    received_at              timestamptz not null,
    internal_notes           text,
    idempotency_key          text not null,
    duplicate_fingerprint    char(64) not null,
    send_acknowledgement     boolean not null default false,
    recorded_by_user_id      uuid not null references app_user (id),
    verified_by_user_id      uuid references app_user (id),
    verified_at              timestamptz,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    constraint offline_financial_record_type_check check (
        record_type in ('CONTRIBUTION', 'SPONSORSHIP', 'ORDER')
    ),
    constraint offline_financial_payment_method_check check (
        payment_method in ('CASH', 'CHECK', 'ACH', 'EXTERNAL_CARD', 'VENMO', 'ZELLE', 'OTHER')
    ),
    constraint offline_financial_verification_status_check check (
        verification_status in ('PENDING_VERIFICATION', 'VERIFIED')
    ),
    constraint offline_financial_amount_check check (amount_minor > 0),
    constraint offline_financial_verified_fields_check check (
        (verification_status = 'PENDING_VERIFICATION' and verified_by_user_id is null and verified_at is null)
        or
        (verification_status = 'VERIFIED' and verified_by_user_id is not null and verified_at is not null)
    ),
    constraint offline_financial_record_unique unique (record_type, record_id),
    constraint offline_financial_idempotency_unique unique (organization_id, idempotency_key),
    constraint offline_financial_fingerprint_unique unique (organization_id, duplicate_fingerprint)
);

create index offline_financial_organization_idx
    on offline_financial_record (organization_id, verification_status, received_at desc);
create index offline_financial_record_idx
    on offline_financial_record (record_type, record_id);
create index offline_financial_pending_idx
    on offline_financial_record (organization_id, created_at)
    where verification_status = 'PENDING_VERIFICATION';

-- Help Center coverage ships with the feature. This article is role-scoped because
-- recording and verification are organization-manager operations.
insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000011', 'recording-and-verifying-offline-payments',
     'Recording and verifying offline payments',
     'How owners and administrators record cash, checks, external transfers, sponsorships, and manual orders without claiming LeagueLift processed the money.',
     E'## What an offline record means\n\nUse **Financial Operations** only after your organization receives money outside LeagueLift, such as cash, a check, ACH, Venmo, Zelle, or an external card terminal. LeagueLift records the transaction; it does not create a Stripe charge or move the funds.\n\n## Record the transaction\n\n1. Open the organization and choose **Financial Operations**.\n2. Select Contribution, Sponsorship, or Store order.\n3. Choose the related campaign, package, or active manual-product store.\n4. Enter the payer, amount or items, payment method, received date, reference, and internal notes.\n5. Leave **Verify now** off when another owner or administrator should check the receipt or deposit.\n\n## Verify a pending record\n\nCompare the record with the real receipt, check, deposit, or external processor. Select **Verify** only when the amount and source are confirmed. Verification updates the related contribution, sponsorship, or order and appends balanced offline-settlement ledger entries. It does not create payout-eligible LeagueLift earnings because LeagueLift never held those funds.\n\n## Corrections\n\nDo not edit or delete confirmed financial history. Controlled void and reversal workflows are a later Phase 18 capability. Until then, contact LeagueLift support before attempting to correct a verified offline record.',
     'Fees and Payments', 'OWNER_ADMIN', 'PUBLISHED', 45, now());

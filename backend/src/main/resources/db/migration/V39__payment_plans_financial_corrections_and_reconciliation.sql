-- Phase 18 slices 4-6: formal fee payment plans, controlled financial
-- corrections, and durable reconciliation history. Financial source and ledger
-- rows remain append-only; corrections are separate linked records.

create table fee_payment_plan (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    fee_assignment_id   uuid not null references fee_assignment (id),
    household_id        uuid not null references household (id),
    status              text not null default 'ACTIVE',
    total_minor         bigint not null,
    currency            text not null default 'USD',
    note                text,
    created_by_user_id  uuid not null references app_user (id),
    cancelled_by_user_id uuid references app_user (id),
    cancelled_at        timestamptz,
    cancel_reason       text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint fee_payment_plan_status_check check (status in ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    constraint fee_payment_plan_total_check check (total_minor > 0),
    constraint fee_payment_plan_cancel_fields_check check (
        (status != 'CANCELLED' and cancelled_by_user_id is null and cancelled_at is null and cancel_reason is null)
        or
        (status = 'CANCELLED' and cancelled_by_user_id is not null and cancelled_at is not null and cancel_reason is not null)
    )
);

create unique index fee_payment_plan_one_active_idx
    on fee_payment_plan (fee_assignment_id) where status = 'ACTIVE';
create index fee_payment_plan_org_idx on fee_payment_plan (organization_id, status, created_at desc);

create table fee_installment (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    payment_plan_id     uuid not null references fee_payment_plan (id),
    sequence_number     integer not null,
    amount_minor        bigint not null,
    due_date            date not null,
    created_at          timestamptz not null default now(),
    constraint fee_installment_amount_check check (amount_minor > 0),
    constraint fee_installment_sequence_check check (sequence_number > 0),
    constraint fee_installment_sequence_unique unique (payment_plan_id, sequence_number)
);

create index fee_installment_plan_idx on fee_installment (payment_plan_id, due_date, sequence_number);

create table fee_payment_installment_allocation (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    payment_plan_id     uuid not null references fee_payment_plan (id),
    installment_id      uuid not null references fee_installment (id),
    fee_payment_id      uuid not null references fee_payment (id),
    amount_minor        bigint not null,
    created_at          timestamptz not null default now(),
    constraint fee_payment_installment_allocation_amount_check check (amount_minor > 0),
    constraint fee_payment_installment_allocation_unique unique (fee_payment_id, installment_id)
);

create index fee_payment_installment_plan_idx on fee_payment_installment_allocation (payment_plan_id);
create index fee_payment_installment_payment_idx on fee_payment_installment_allocation (fee_payment_id);

alter table offline_financial_record add column reversed_by_user_id uuid references app_user (id);
alter table offline_financial_record add column reversed_at timestamptz;
alter table offline_financial_record add column reversal_reason text;
alter table offline_financial_record drop constraint offline_financial_verification_status_check;
alter table offline_financial_record add constraint offline_financial_verification_status_check
    check (verification_status in ('PENDING_VERIFICATION', 'VERIFIED', 'REVERSED'));
alter table offline_financial_record drop constraint offline_financial_verified_fields_check;
alter table offline_financial_record add constraint offline_financial_verified_fields_check check (
    (verification_status = 'PENDING_VERIFICATION' and verified_by_user_id is null and verified_at is null
        and reversed_by_user_id is null and reversed_at is null and reversal_reason is null)
    or
    (verification_status = 'VERIFIED' and verified_by_user_id is not null and verified_at is not null
        and reversed_by_user_id is null and reversed_at is null and reversal_reason is null)
    or
    (verification_status = 'REVERSED' and verified_by_user_id is not null and verified_at is not null
        and reversed_by_user_id is not null and reversed_at is not null and reversal_reason is not null)
);

alter table ledger_entry drop constraint ledger_entry_type_check;
alter table ledger_entry add constraint ledger_entry_type_check check (entry_type in (
    'CONTRIBUTION', 'GROSS_SALE', 'PRODUCTION_COST', 'LEAGUELIFT_PLATFORM_FEE',
    'ORGANIZATION_EARNING', 'TRANSFER', 'REFUND', 'OFFLINE_SETTLEMENT', 'MANUAL_ADJUSTMENT'
));
alter table ledger_entry drop constraint ledger_entry_source_type_check;
alter table ledger_entry add constraint ledger_entry_source_type_check
    check (source_type in ('CONTRIBUTION', 'ORDER', 'TRANSFER', 'REFUND', 'SPONSORSHIP', 'CORRECTION'));

create table financial_correction (
    id                   uuid primary key default gen_random_uuid(),
    organization_id      uuid not null references organization (id),
    correction_type      text not null,
    target_type          text not null,
    target_id            uuid not null,
    amount_minor         bigint not null,
    currency             text not null default 'USD',
    reason               text not null,
    provider_reference   text,
    confirmation_hash    char(64) not null,
    idempotency_key      text not null,
    created_by_user_id   uuid not null references app_user (id),
    created_at           timestamptz not null default now(),
    constraint financial_correction_type_check check (correction_type in ('REFUND', 'REVERSAL')),
    constraint financial_correction_target_check check (target_type in ('CONTRIBUTION', 'SPONSORSHIP', 'ORDER', 'OFFLINE_FINANCIAL_RECORD')),
    constraint financial_correction_amount_check check (amount_minor > 0),
    constraint financial_correction_idempotency_unique unique (organization_id, idempotency_key)
);

create index financial_correction_target_idx on financial_correction (organization_id, target_type, target_id, created_at desc);

create table reconciliation_run (
    id                   uuid primary key default gen_random_uuid(),
    organization_id      uuid not null references organization (id),
    status               text not null,
    issue_count           integer not null default 0,
    high_count            integer not null default 0,
    medium_count          integer not null default 0,
    low_count             integer not null default 0,
    started_by_user_id    uuid not null references app_user (id),
    started_at            timestamptz not null default now(),
    completed_at          timestamptz,
    constraint reconciliation_run_status_check check (status in ('RUNNING', 'COMPLETED', 'FAILED')),
    constraint reconciliation_run_counts_check check (issue_count >= 0 and high_count >= 0 and medium_count >= 0 and low_count >= 0)
);

create index reconciliation_run_org_idx on reconciliation_run (organization_id, started_at desc);

create table reconciliation_issue (
    id                   uuid primary key default gen_random_uuid(),
    reconciliation_run_id uuid not null references reconciliation_run (id),
    organization_id      uuid not null references organization (id),
    issue_type           text not null,
    severity             text not null,
    resource_type        text not null,
    resource_id          uuid,
    title                text not null,
    detail               text not null,
    action_path          text,
    created_at           timestamptz not null default now(),
    constraint reconciliation_issue_severity_check check (severity in ('HIGH', 'MEDIUM', 'LOW'))
);

create index reconciliation_issue_run_idx on reconciliation_issue (reconciliation_run_id, severity, created_at);

update support_article
set body_markdown = replace(
    body_markdown,
    'Controlled void and reversal workflows are a later Phase 18 capability. Until then, contact LeagueLift support before attempting to correct a verified offline record.',
    'Use **Financial Operations → Controlled refunds & reversals** to preview and reverse a verified offline record. The original record remains visible and opposite ledger entries are appended.'
), updated_at = now()
where slug = 'recording-and-verifying-offline-payments';

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000012', 'creating-and-managing-fee-payment-plans',
     'Creating and managing fee payment plans',
     'How owners and administrators split an outstanding household fee into dated installments and track payment progress.',
     E'## Create a plan\n\nOpen **Fees & Payments**, choose a household fee, and select **Create payment plan**. Enter two or more installments whose amounts add up exactly to the current outstanding balance. Due dates must be in order.\n\n## How payments are applied\n\nNew fee payments are allocated to the earliest unpaid installment first. Voiding a payment preserves its history and removes its allocation from installment progress.\n\n## Cancel or replace a plan\n\nCanceling a plan does not void any payment. It closes the remaining schedule. Create a new plan only for the fee balance that is still outstanding.',
     'Fees and Payments', 'OWNER_ADMIN', 'PUBLISHED', 46, now()),
    ('10000000-0000-4000-8000-000000000013', 'previewing-refunds-and-financial-reversals',
     'Previewing refunds and financial reversals',
     'How managers preview and confirm a refund or reverse an incorrectly verified offline record without deleting history.',
     E'## Always preview first\n\nOpen **Financial Operations** and choose **Corrections**. Select the record type and enter its record ID. LeagueLift shows the maximum correctable amount, prior corrections, and the exact action before execution.\n\n## Online refunds\n\nStripe refunds can be partial or full within the configured refund window. A full refund changes the source record to Refunded. A partial refund leaves it confirmed and records the refunded amount as a separate correction.\n\n## Offline reversals\n\nA verified offline record can only be fully reversed. LeagueLift keeps the original record, marks it Reversed, and appends opposite ledger entries. Never edit or delete the original financial history.',
     'Fees and Payments', 'OWNER_ADMIN', 'PUBLISHED', 47, now()),
    ('10000000-0000-4000-8000-000000000014', 'running-financial-reconciliation',
     'Running financial reconciliation',
     'How owners and administrators scan for missing provider references, ledger gaps, fulfillment exceptions, overdue installments, and pending offline verification.',
     E'## Run a reconciliation\n\nOpen **Financial Operations** and choose **Reconciliation**. Run a new scan after imports, corrections, refunds, or fulfillment updates. Each run is preserved as a timestamped snapshot.\n\n## Review issues\n\nHigh-priority issues indicate a financial or fulfillment record that needs prompt review. Medium and low issues cover pending verification, overdue installments, or mismatches that may be expected but still require confirmation. Use each issue link to open the affected workflow.\n\n## Do not repair data directly\n\nUse LeagueLift correction, verification, fulfillment, and payment-plan controls. Do not update financial tables or ledger rows manually.',
     'Troubleshooting', 'OWNER_ADMIN', 'PUBLISHED', 48, now());

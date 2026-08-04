-- Phase 19 slices 19.5-19.8 (ADR-056): QuickBooks Online and sports-data
-- provider scaffolds, provider-neutral sync/issue history, platform-provider
-- contract readiness, and Help Center coverage. No provider is activated and no
-- undocumented HTTP call is introduced by this migration.

create table integration_sync_run (
    id                       uuid primary key default gen_random_uuid(),
    connection_id            uuid references integration_connection (id),
    provider                 text not null references integration_provider_catalog (provider),
    owner_type               text not null,
    organization_id          uuid references organization (id),
    user_id                  uuid references app_user (id),
    direction                text not null,
    trigger_type             text not null,
    status                   text not null,
    idempotency_key          text,
    cursor_value             text,
    checkpoint_json          jsonb not null default '{}'::jsonb,
    discovered_count         integer not null default 0,
    created_count            integer not null default 0,
    updated_count            integer not null default 0,
    skipped_count            integer not null default 0,
    failed_count             integer not null default 0,
    rate_limit_remaining     integer,
    rate_limit_resets_at     timestamptz,
    error_code               text,
    error_message            text,
    requested_by_user_id     uuid references app_user (id),
    requested_at             timestamptz not null default now(),
    started_at               timestamptz,
    completed_at             timestamptz,
    constraint integration_sync_owner_type_check check (owner_type in ('PLATFORM', 'ORGANIZATION', 'USER')),
    constraint integration_sync_owner_check check (
        (owner_type = 'PLATFORM' and organization_id is null and user_id is null) or
        (owner_type = 'ORGANIZATION' and organization_id is not null and user_id is null) or
        (owner_type = 'USER' and organization_id is null and user_id is not null)
    ),
    constraint integration_sync_direction_check check (direction in ('READ', 'WRITE', 'WEBHOOK', 'HEALTH')),
    constraint integration_sync_trigger_check check (trigger_type in ('MANUAL', 'SCHEDULED', 'OUTBOX', 'WEBHOOK', 'STUB')),
    constraint integration_sync_status_check check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    constraint integration_sync_counts_check check (
        discovered_count >= 0 and created_count >= 0 and updated_count >= 0 and skipped_count >= 0 and failed_count >= 0
    ),
    constraint integration_sync_completion_check check (
        (status in ('SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED') and completed_at is not null) or
        (status in ('QUEUED', 'RUNNING'))
    )
);

create unique index integration_sync_org_idempotency_idx
    on integration_sync_run (organization_id, provider, idempotency_key)
    where owner_type = 'ORGANIZATION' and idempotency_key is not null;
create unique index integration_sync_user_idempotency_idx
    on integration_sync_run (user_id, provider, idempotency_key)
    where owner_type = 'USER' and idempotency_key is not null;
create unique index integration_sync_platform_idempotency_idx
    on integration_sync_run (provider, idempotency_key)
    where owner_type = 'PLATFORM' and idempotency_key is not null;
create index integration_sync_org_history_idx on integration_sync_run (organization_id, requested_at desc);
create index integration_sync_connection_history_idx on integration_sync_run (connection_id, requested_at desc);
create index integration_sync_failures_idx on integration_sync_run (status, requested_at desc)
    where status in ('PARTIAL', 'FAILED');

create table integration_sync_issue (
    id                    uuid primary key default gen_random_uuid(),
    sync_run_id           uuid not null references integration_sync_run (id),
    severity              text not null,
    code                  text not null,
    message               text not null,
    external_entity_type  text,
    external_entity_id    text,
    internal_entity_type  text,
    internal_entity_id    uuid,
    retryable             boolean not null default false,
    details_json          jsonb not null default '{}'::jsonb,
    created_at            timestamptz not null default now(),
    constraint integration_sync_issue_severity_check check (severity in ('INFO', 'WARNING', 'ERROR'))
);

create index integration_sync_issue_run_idx on integration_sync_issue (sync_run_id, severity, created_at);

create table quickbooks_connection_setting (
    connection_id          uuid primary key references integration_connection (id),
    realm_id               text,
    company_name           text,
    environment            text not null default 'SANDBOX',
    export_policy          text not null default 'READ_ONLY',
    accounting_basis       text not null default 'ACCRUAL',
    default_currency       char(3),
    last_company_read_at   timestamptz,
    last_accounts_read_at  timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    constraint quickbooks_environment_check check (environment in ('SANDBOX', 'PRODUCTION')),
    constraint quickbooks_export_policy_check check (export_policy in ('READ_ONLY', 'EXPORT_PREVIEW_ONLY')),
    constraint quickbooks_basis_check check (accounting_basis in ('ACCRUAL', 'CASH'))
);

create table quickbooks_account_mapping (
    id                    uuid primary key default gen_random_uuid(),
    connection_id         uuid not null references integration_connection (id),
    mapping_type          text not null,
    external_account_id   text not null,
    external_account_name text not null,
    external_account_type text,
    active                boolean not null default true,
    configured_by_user_id uuid not null references app_user (id),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint quickbooks_mapping_type_check check (mapping_type in (
        'SALES_INCOME', 'CONTRIBUTION_INCOME', 'SPONSORSHIP_INCOME', 'REFUNDS',
        'FEES_RECEIVABLE', 'BANK_CLEARING', 'PAYOUT_CLEARING'
    ))
);

create unique index quickbooks_mapping_type_unique_idx
    on quickbooks_account_mapping (connection_id, mapping_type) where active;

create table quickbooks_export_batch (
    id                    uuid primary key default gen_random_uuid(),
    connection_id         uuid not null references integration_connection (id),
    organization_id       uuid not null references organization (id),
    sync_run_id           uuid references integration_sync_run (id),
    status                text not null,
    period_start          date not null,
    period_end            date not null,
    candidate_count       integer not null default 0,
    exported_count        integer not null default 0,
    failed_count          integer not null default 0,
    idempotency_key       text not null,
    requested_by_user_id  uuid not null references app_user (id),
    created_at            timestamptz not null default now(),
    completed_at          timestamptz,
    constraint quickbooks_export_status_check check (status in ('PREVIEWED', 'READY', 'BLOCKED', 'EXPORTED', 'PARTIAL', 'FAILED')),
    constraint quickbooks_export_dates_check check (period_end >= period_start),
    constraint quickbooks_export_counts_check check (candidate_count >= 0 and exported_count >= 0 and failed_count >= 0)
);

create unique index quickbooks_export_idempotency_idx
    on quickbooks_export_batch (organization_id, idempotency_key);

create table quickbooks_export_item (
    id                    uuid primary key default gen_random_uuid(),
    batch_id              uuid not null references quickbooks_export_batch (id),
    source_type           text not null,
    source_id             uuid not null,
    external_transaction_id text,
    status                text not null,
    payload_hash          char(64) not null,
    error_code            text,
    error_message         text,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint quickbooks_export_item_status_check check (status in ('PENDING', 'EXPORTED', 'SKIPPED', 'FAILED'))
);

create unique index quickbooks_export_item_source_unique_idx
    on quickbooks_export_item (batch_id, source_type, source_id);

create table sports_data_mapping (
    id                    uuid primary key default gen_random_uuid(),
    connection_id         uuid not null references integration_connection (id),
    provider              text not null references integration_provider_catalog (provider),
    entity_type           text not null,
    internal_entity_id    uuid,
    external_entity_id    text not null,
    external_parent_id    text,
    external_hash         char(64),
    mapping_json          jsonb not null default '{}'::jsonb,
    last_seen_at          timestamptz not null default now(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint sports_data_provider_check check (provider in ('SPORTSENGINE', 'GAMECHANGER', 'MAXPREPS')),
    constraint sports_data_entity_type_check check (entity_type in ('ORGANIZATION', 'TEAM', 'PARTICIPANT', 'ROSTER_MEMBERSHIP', 'EVENT'))
);

create unique index sports_data_mapping_external_unique_idx
    on sports_data_mapping (connection_id, entity_type, external_entity_id);
create unique index sports_data_mapping_internal_unique_idx
    on sports_data_mapping (connection_id, entity_type, internal_entity_id)
    where internal_entity_id is not null;

create table sports_data_import_run (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    connection_id         uuid references integration_connection (id),
    provider              text not null references integration_provider_catalog (provider),
    sync_run_id           uuid references integration_sync_run (id),
    source_mode           text not null,
    status                text not null,
    commit_allowed        boolean not null default false,
    discovered_count      integer not null default 0,
    valid_count           integer not null default 0,
    duplicate_count       integer not null default 0,
    conflict_count        integer not null default 0,
    error_count           integer not null default 0,
    preview_hash          char(64) not null,
    requested_by_user_id  uuid not null references app_user (id),
    requested_at          timestamptz not null default now(),
    completed_at          timestamptz,
    constraint sports_import_provider_check check (provider in ('SPORTSENGINE', 'GAMECHANGER', 'MAXPREPS')),
    constraint sports_import_source_check check (source_mode in ('OAUTH', 'API_TOKEN', 'FILE_IMPORT', 'ICS')),
    constraint sports_import_status_check check (status in ('PREVIEWED', 'READY', 'BLOCKED', 'IMPORTED', 'PARTIAL', 'FAILED')),
    constraint sports_import_counts_check check (
        discovered_count >= 0 and valid_count >= 0 and duplicate_count >= 0 and conflict_count >= 0 and error_count >= 0
    )
);

create index sports_import_org_history_idx on sports_data_import_run (organization_id, requested_at desc);

create table sports_data_import_issue (
    id                    uuid primary key default gen_random_uuid(),
    import_run_id         uuid not null references sports_data_import_run (id),
    row_number            integer,
    entity_type           text,
    external_entity_id    text,
    severity              text not null,
    code                  text not null,
    message               text not null,
    created_at            timestamptz not null default now(),
    constraint sports_import_issue_severity_check check (severity in ('WARNING', 'ERROR')),
    constraint sports_import_issue_row_check check (row_number is null or row_number > 0)
);

create index sports_import_issue_run_idx on sports_data_import_issue (import_run_id, severity, row_number);

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000018', 'quickbooks-online-readiness',
     'Preparing QuickBooks Online for Rally26',
     'What owners and administrators can prepare before QuickBooks authorization and exports are activated.',
     E'## Who manages the connection\n\nOnly an organization owner or administrator can connect QuickBooks Online. The connection belongs to the organization, not an individual bookkeeper account inside Rally26.\n\n## Phase 19 readiness\n\nRally26 models the company realm, chart-of-accounts mapping, export batches, idempotency, token refresh, revocation, and reconciliation results. Until Rally26 activates and verifies its Intuit application, the card remains **Not configured** and no accounting data is sent.\n\n## Account mapping\n\nBefore an export can be enabled, review mappings for sales income, contributions, sponsorship income, refunds, fee receivables, bank clearing, and payout clearing. Phase 19 is preview/read-only. Accounting writes require sandbox verification and an approved accounting policy in Phase 20.\n\n## Existing financial history\n\nQuickBooks export never replaces Rally26 ledger history. Export batches retain source identity and payload hashes so retries remain idempotent.',
     'Fees and Payments', 'OWNER_ADMIN', 'PUBLISHED', 58, now()),
    ('10000000-0000-4000-8000-000000000019', 'sportsengine-integration-readiness',
     'SportsEngine integration readiness',
     'How SportsEngine connection, mapping, previews, and activation will work.',
     E'## Current status\n\nSportsEngine remains **Not configured** until Rally26 verifies the current official product, endpoints, scopes, rate limits, and terms. Legacy API documentation is not treated as current access.\n\n## Planned workflow\n\nAn owner or administrator authorizes the organization, reviews mapped organizations, teams, participants, roster memberships, and events, resolves conflicts, and confirms an idempotent import. Every imported record retains provider and external identity.\n\n## Safety\n\nPhase 19 provides preview and mapping contracts only. It does not silently create or overwrite Rally26 records and it never asks for an undocumented token.',
     'Organization Setup', 'OWNER_ADMIN', 'PUBLISHED', 59, now()),
    ('10000000-0000-4000-8000-000000000020', 'gamechanger-maxpreps-imports',
     'Using GameChanger or MaxPreps data',
     'Why direct connections remain partner-pending and which reviewed import workflows are supported.',
     E'## Partner-pending providers\n\nRally26 does not claim a direct GameChanger or MaxPreps API connection without an official supported contract. It does not scrape or automate either consumer website.\n\n## Supported paths\n\nUse provider-supported CSV or calendar exports where available, then preview mappings and validation results before importing. Existing Rally26 CSV event import and ICS feed workflows remain available.\n\n## Future activation\n\nIf an official partner interface becomes available, the existing external-ID, mapping, sync-run, issue, disconnect, and recovery scaffolding can be connected to the verified provider client without redesigning the user workflow.',
     'Events and RSVP', 'OWNER_ADMIN', 'PUBLISHED', 60, now()),
    ('10000000-0000-4000-8000-000000000021', 'integration-sync-history-and-errors',
     'Understanding integration sync history and errors',
     'How owners and Platform Administrators review provider runs, counts, warnings, failures, and recovery state.',
     E'## Sync history\n\nEach provider run records who or what triggered it, direction, start and completion times, discovered/created/updated/skipped/failed counts, rate-limit metadata, and a redacted error. Detailed issues identify the affected external or Rally26 record without exposing tokens.\n\n## Statuses\n\n**Succeeded** completed without record failures. **Partial** completed but retained one or more issues. **Failed** did not complete. A retry uses a new run or an idempotent provider operation; history is never rewritten.\n\n## Platform providers\n\nPlatform readiness shows configuration and contract capabilities, not secret values or a fabricated live health result. Credentialed probes and webhook lifecycle verification belong to Phase 20.',
     'Troubleshooting', 'OWNER_ADMIN', 'PUBLISHED', 61, now());

-- Phase 19 slices 19.0-19.2 (ADR-054): provider catalog, generalized
-- user/organization/platform connection records, encrypted credential references,
-- OAuth state/PKCE lifecycle, durable health and connection history. This migration
-- does not activate a provider or store any plaintext credential.

create table integration_provider_catalog (
    provider                 text primary key,
    display_name             text not null,
    category                 text not null,
    ownership_scope          text not null,
    primary_auth_mode        text not null,
    supported_auth_modes     jsonb not null default '[]'::jsonb,
    baseline_readiness       text not null,
    adapter_mode             text not null,
    description              text not null,
    activation_requirement   text not null,
    default_scopes           jsonb not null default '[]'::jsonb,
    sort_order               integer not null default 0,
    visible_to_customers     boolean not null default true,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    constraint integration_provider_category_check check (category in (
        'PAYMENTS', 'FULFILLMENT', 'COMMUNICATIONS', 'STORAGE', 'CALENDAR',
        'ACCOUNTING', 'SPORTS_DATA', 'MAPS'
    )),
    constraint integration_provider_owner_check check (ownership_scope in ('PLATFORM', 'ORGANIZATION', 'USER')),
    constraint integration_provider_auth_check check (primary_auth_mode in (
        'NONE', 'PLATFORM_SECRET', 'OAUTH2', 'API_TOKEN', 'FILE_IMPORT', 'URL', 'WEBHOOK'
    )),
    constraint integration_provider_readiness_check check (baseline_readiness in (
        'AVAILABLE', 'NOT_CONFIGURED', 'PARTNER_PENDING', 'PLATFORM_MANAGED', 'UNSUPPORTED'
    )),
    constraint integration_provider_adapter_mode_check check (adapter_mode in (
        'EXISTING', 'OAUTH_SCAFFOLD', 'FILE_IMPORT', 'PARTNER_PENDING', 'PLATFORM_MANAGED'
    )),
    constraint integration_provider_sort_check check (sort_order between 0 and 10000)
);

insert into integration_provider_catalog
    (provider, display_name, category, ownership_scope, primary_auth_mode, supported_auth_modes,
     baseline_readiness, adapter_mode, description, activation_requirement, default_scopes, sort_order, visible_to_customers)
values
    ('GOOGLE_CALENDAR', 'Google Calendar', 'CALENDAR', 'USER', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Optional personal calendar synchronization. Standard ICS downloads remain available without a connection.',
     'Requires a verified Google Cloud OAuth application, approved callback, narrow scopes, and credentialed testing in Phase 20.',
     '["calendar.events"]', 10, true),
    ('QUICKBOOKS_ONLINE', 'QuickBooks Online', 'ACCOUNTING', 'ORGANIZATION', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Organization accounting export and reconciliation connection for owners and administrators.',
     'Requires an Intuit application, sandbox company, approved account mapping, and accounting write policy in Phase 20.',
     '["com.intuit.quickbooks.accounting"]', 20, true),
    ('SPORTSENGINE', 'SportsEngine', 'SPORTS_DATA', 'ORGANIZATION', 'OAUTH2', '["OAUTH2","API_TOKEN"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Organization, team, roster, and schedule mapping scaffold pending verified current provider access.',
     'Requires confirmed current SportsEngine product access, official endpoints, scopes, and terms.',
     '[]', 30, true),
    ('GAMECHANGER', 'GameChanger', 'SPORTS_DATA', 'ORGANIZATION', 'FILE_IMPORT', '["FILE_IMPORT","URL"]',
     'PARTNER_PENDING', 'PARTNER_PENDING',
     'Supported CSV and calendar workflows remain available while direct provider access is partner-pending.',
     'Requires an official supported partner/API/export contract. No undocumented HTTP client is included.',
     '[]', 40, true),
    ('MAXPREPS', 'MaxPreps', 'SPORTS_DATA', 'ORGANIZATION', 'FILE_IMPORT', '["FILE_IMPORT"]',
     'PARTNER_PENDING', 'PARTNER_PENDING',
     'File-import and partner workflow scaffold only; Rally26 does not scrape the consumer website.',
     'Requires a formal partner mechanism or supported export/import contract.',
     '[]', 50, true),
    ('ICS_FEED', 'ICS Feed', 'CALENDAR', 'ORGANIZATION', 'URL', '["URL"]',
     'AVAILABLE', 'EXISTING',
     'Stateful schedule feed subscription already available in Rally26.',
     'No vendor credential is required. The organization supplies an HTTPS feed URL.',
     '[]', 60, true),
    ('CSV_IMPORT', 'CSV Event Import', 'SPORTS_DATA', 'ORGANIZATION', 'FILE_IMPORT', '["FILE_IMPORT"]',
     'AVAILABLE', 'FILE_IMPORT',
     'One-time reviewed event import with stable external identifiers and duplicate protection.',
     'No vendor credential is required.',
     '[]', 70, true),
    ('STRIPE', 'Stripe', 'PAYMENTS', 'PLATFORM', 'PLATFORM_SECRET', '["PLATFORM_SECRET","WEBHOOK"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Rally26-managed payment and payout infrastructure.',
     'Configured and verified by Rally26; organizations never enter platform Stripe credentials.', '[]', 100, false),
    ('PRINTIFY', 'Printify', 'FULFILLMENT', 'PLATFORM', 'PLATFORM_SECRET', '["PLATFORM_SECRET","WEBHOOK"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Rally26-managed apparel catalog and fulfillment infrastructure.',
     'Configured and verified by Rally26; organizations never enter platform Printify credentials.', '[]', 110, false),
    ('RESEND', 'Resend', 'COMMUNICATIONS', 'PLATFORM', 'PLATFORM_SECRET', '["PLATFORM_SECRET","WEBHOOK"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Rally26-managed transactional email delivery.',
     'Configured and verified by Rally26; organizations never enter email-provider credentials.', '[]', 120, false),
    ('TWILIO', 'Twilio', 'COMMUNICATIONS', 'PLATFORM', 'PLATFORM_SECRET', '["PLATFORM_SECRET","WEBHOOK"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Rally26-managed opted-in transactional SMS delivery.',
     'Configured and verified by Rally26; organizations never enter SMS-provider credentials.', '[]', 130, false),
    ('DIGITALOCEAN_SPACES', 'DigitalOcean Spaces', 'STORAGE', 'PLATFORM', 'PLATFORM_SECRET', '["PLATFORM_SECRET"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Rally26-managed private media and document storage.',
     'Configured and verified by Rally26; permanent storage credentials are never browser-visible.', '[]', 140, false),
    ('GOOGLE_MAPS', 'Google Maps', 'MAPS', 'PLATFORM', 'NONE', '["NONE"]',
     'PLATFORM_MANAGED', 'PLATFORM_MANAGED', 'Safe external directions links; no customer connection is required.',
     'Richer APIs remain disabled until a Google Cloud key, privacy review, and billing review exist.', '[]', 150, false);

create table integration_credential_secret (
    id                    uuid primary key default gen_random_uuid(),
    provider              text not null references integration_provider_catalog (provider),
    owner_type            text not null,
    organization_id       uuid references organization (id),
    user_id               uuid references app_user (id),
    credential_kind       text not null,
    ciphertext            text not null,
    key_version           integer not null,
    aad_context           text not null,
    rotated_from_id       uuid references integration_credential_secret (id),
    created_by_user_id    uuid references app_user (id),
    created_at            timestamptz not null default now(),
    revoked_at            timestamptz,
    constraint integration_credential_owner_type_check check (owner_type in ('PLATFORM', 'ORGANIZATION', 'USER')),
    constraint integration_credential_kind_check check (credential_kind in ('OAUTH_TOKEN_SET', 'API_TOKEN', 'OAUTH_PKCE_VERIFIER')),
    constraint integration_credential_key_version_check check (key_version > 0),
    constraint integration_credential_owner_check check (
        (owner_type = 'PLATFORM' and organization_id is null and user_id is null) or
        (owner_type = 'ORGANIZATION' and organization_id is not null and user_id is null) or
        (owner_type = 'USER' and organization_id is null and user_id is not null)
    )
);

create index integration_credential_owner_idx on integration_credential_secret (owner_type, organization_id, user_id, provider, created_at desc);

create table integration_connection (
    id                         uuid primary key default gen_random_uuid(),
    provider                   text not null references integration_provider_catalog (provider),
    category                   text not null,
    owner_type                 text not null,
    organization_id            uuid references organization (id),
    user_id                    uuid references app_user (id),
    auth_mode                  text not null,
    status                     text not null,
    granted_scopes             jsonb not null default '[]'::jsonb,
    external_account_id        text,
    external_account_name      text,
    credential_id              uuid references integration_credential_secret (id),
    access_token_expires_at    timestamptz,
    refresh_locked_at          timestamptz,
    refresh_locked_by_user_id  uuid references app_user (id),
    last_successful_sync_at    timestamptz,
    last_health_check_at       timestamptz,
    last_error_code            text,
    last_error_message         text,
    legacy_resource_type       text,
    legacy_resource_id         uuid,
    created_by_user_id         uuid references app_user (id),
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    connected_at               timestamptz,
    revoked_at                 timestamptz,
    disconnected_at            timestamptz,
    constraint integration_connection_category_check check (category in (
        'PAYMENTS', 'FULFILLMENT', 'COMMUNICATIONS', 'STORAGE', 'CALENDAR',
        'ACCOUNTING', 'SPORTS_DATA', 'MAPS'
    )),
    constraint integration_connection_owner_type_check check (owner_type in ('PLATFORM', 'ORGANIZATION', 'USER')),
    constraint integration_connection_auth_check check (auth_mode in (
        'NONE', 'PLATFORM_SECRET', 'OAUTH2', 'API_TOKEN', 'FILE_IMPORT', 'URL', 'WEBHOOK'
    )),
    constraint integration_connection_status_check check (status in (
        'NOT_CONFIGURED', 'AVAILABLE', 'AUTHORIZATION_PENDING', 'CONNECTED',
        'DEGRADED', 'REVOKED', 'DISCONNECTED', 'UNSUPPORTED'
    )),
    constraint integration_connection_owner_check check (
        (owner_type = 'PLATFORM' and organization_id is null and user_id is null) or
        (owner_type = 'ORGANIZATION' and organization_id is not null and user_id is null) or
        (owner_type = 'USER' and organization_id is null and user_id is not null)
    ),
    constraint integration_connection_connected_credential_check check (
        status not in ('CONNECTED', 'DEGRADED') or auth_mode not in ('OAUTH2', 'API_TOKEN') or credential_id is not null
    )
);

create index integration_connection_org_idx on integration_connection (organization_id, provider, updated_at desc);
create index integration_connection_user_idx on integration_connection (user_id, provider, updated_at desc);
create unique index integration_connection_active_org_provider_idx
    on integration_connection (organization_id, provider)
    where owner_type = 'ORGANIZATION'
      and provider not in ('ICS_FEED')
      and status in ('AUTHORIZATION_PENDING', 'CONNECTED', 'DEGRADED');
create unique index integration_connection_active_user_provider_idx
    on integration_connection (user_id, provider)
    where owner_type = 'USER'
      and status in ('AUTHORIZATION_PENDING', 'CONNECTED', 'DEGRADED');
create unique index integration_connection_legacy_unique_idx
    on integration_connection (legacy_resource_type, legacy_resource_id)
    where legacy_resource_id is not null;

create table integration_connection_event (
    id                    uuid primary key default gen_random_uuid(),
    connection_id         uuid not null references integration_connection (id),
    organization_id       uuid references organization (id),
    user_id               uuid references app_user (id),
    event_type            text not null,
    status_from           text,
    status_to             text,
    actor_user_id         uuid references app_user (id),
    metadata              jsonb not null default '{}'::jsonb,
    created_at            timestamptz not null default now()
);

create index integration_connection_event_connection_idx on integration_connection_event (connection_id, created_at desc);

create table integration_oauth_state (
    id                       uuid primary key default gen_random_uuid(),
    provider                 text not null references integration_provider_catalog (provider),
    connection_id            uuid not null references integration_connection (id),
    owner_type               text not null,
    organization_id          uuid references organization (id),
    user_id                  uuid references app_user (id),
    state_hash               char(64) not null,
    code_verifier_ciphertext text not null,
    key_version              integer not null,
    aad_context              text not null,
    redirect_uri             text not null,
    requested_scopes         jsonb not null default '[]'::jsonb,
    expires_at               timestamptz not null,
    consumed_at              timestamptz,
    created_by_user_id       uuid not null references app_user (id),
    created_at               timestamptz not null default now(),
    constraint integration_oauth_owner_type_check check (owner_type in ('ORGANIZATION', 'USER')),
    constraint integration_oauth_owner_check check (
        (owner_type = 'ORGANIZATION' and organization_id is not null and user_id is null) or
        (owner_type = 'USER' and organization_id is null and user_id is not null)
    ),
    constraint integration_oauth_key_version_check check (key_version > 0),
    constraint integration_oauth_expiry_check check (expires_at > created_at)
);

create unique index integration_oauth_state_hash_unique_idx on integration_oauth_state (state_hash);
create index integration_oauth_state_expiry_idx on integration_oauth_state (expires_at) where consumed_at is null;

create table integration_connection_health_check (
    id                    uuid primary key default gen_random_uuid(),
    connection_id         uuid not null references integration_connection (id),
    status                text not null,
    latency_ms            bigint,
    error_code            text,
    error_message         text,
    checked_by_user_id    uuid references app_user (id),
    checked_at            timestamptz not null default now(),
    constraint integration_health_status_check check (status in ('HEALTHY', 'DEGRADED', 'FAILED')),
    constraint integration_health_latency_check check (latency_ms is null or latency_ms >= 0)
);

create index integration_health_connection_idx on integration_connection_health_check (connection_id, checked_at desc);

-- Existing Phase 12 ICS connections remain authoritative in event_source_connection.
-- Backfill them into the generalized read model so the new catalog can report their
-- state without changing the proven sync/import path in this foundation slice.
insert into integration_connection
    (id, provider, category, owner_type, organization_id, auth_mode, status,
     external_account_name, last_successful_sync_at, last_health_check_at,
     last_error_code, last_error_message, legacy_resource_type, legacy_resource_id,
     created_by_user_id, created_at, updated_at, connected_at, disconnected_at)
select
    gen_random_uuid(), esc.provider, 'CALENDAR', 'ORGANIZATION', esc.organization_id,
    'URL', case when esc.status = 'ACTIVE' then 'CONNECTED' else 'DISCONNECTED' end,
    esc.label, case when esc.last_sync_status = 'SUCCESS' then esc.last_synced_at else null end,
    esc.last_synced_at,
    case when esc.last_sync_status = 'FAILED' then 'ICS_SYNC_FAILED' else null end,
    case when esc.last_sync_status = 'FAILED' then left(esc.last_sync_error, 500) else null end,
    'EVENT_SOURCE_CONNECTION', esc.id, esc.created_by_user_id, esc.created_at, esc.updated_at,
    esc.created_at, case when esc.status = 'DISCONNECTED' then esc.updated_at else null end
from event_source_connection esc
where esc.provider = 'ICS_FEED';

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000015', 'understanding-integration-statuses',
     'Understanding integration statuses',
     'What Available, Not configured, Partner pending, Connected, Needs attention, and Disconnected mean in Rally26.',
     E'## Availability is not a connection\n\n**Available** means Rally26 has a supported connection or import workflow. **Not configured** means the safe adapter is present but Rally26 has not activated the required provider application, credentials, callback, scopes, or contract verification. **Partner pending** means no supported direct API has been verified; use the displayed CSV, ICS, or manual workflow instead.\n\n## Connection states\n\n**Authorization pending** means an OAuth attempt started but did not complete. **Connected** is shown only after a provider authorization or verified platform configuration succeeds. **Needs attention** means the stored connection exists but refresh, health, or sync failed. **Disconnected** and **Revoked** are terminal states until a new authorization starts.\n\n## Credentials\n\nRally26 never returns stored access or refresh tokens to the browser. Organization users should never enter Rally26 platform credentials for Stripe, Printify, Resend, Twilio, or DigitalOcean Spaces.',
     'Troubleshooting', 'OWNER_ADMIN', 'PUBLISHED', 49, now());

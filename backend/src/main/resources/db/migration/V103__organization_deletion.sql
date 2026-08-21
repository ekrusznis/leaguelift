-- Organization closure — third and final slice of App Store account-deletion work
-- (see V101 ownership-transfer-invitations, V102 account_deletion). An Owner who wants
-- out and has no one to transfer to can close the entire organization instead — cascades
-- across every organization-owned table (application-orchestrated, not DB cascade; see
-- OrganizationDeletionLifecycleScanner's own comment for why), except:
--   - financial tables are archived into organization_financial_archive first
--   - audit_event (all partitions) and platform_support_access survive, per founder
--     decision this session, matching how the account-merge feature already treats
--     audit history as permanent
-- The organization row itself is never deleted — it becomes a tombstone
-- (status -> ARCHIVED, an existing, already-checked-against-elsewhere status that no
-- code path has ever actually set until now).
create table organization_deletion_request (
    id                   uuid primary key default gen_random_uuid(),
    organization_id      uuid not null references organization (id),
    requested_by_user_id uuid not null references app_user (id),
    status               text not null default 'PENDING' check (status in ('PENDING', 'CANCELED', 'COMPLETED')),
    requested_at         timestamptz not null default now(),
    scheduled_for        timestamptz not null,
    canceled_at          timestamptz,
    completed_at         timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);

create unique index organization_deletion_request_one_pending_per_org
    on organization_deletion_request (organization_id)
    where status = 'PENDING';

create index organization_deletion_request_scheduled_idx
    on organization_deletion_request (scheduled_for)
    where status = 'PENDING';

-- One generic table, not 14 mirrored ones — a full row snapshot (to_jsonb) is simpler
-- and still fully queryable, and this is a one-way archive, never read back into the
-- live schema.
create table organization_financial_archive (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    source_table    text not null,
    source_id       uuid not null,
    snapshot_json   jsonb not null,
    archived_at     timestamptz not null default now()
);

create index organization_financial_archive_organization_idx
    on organization_financial_archive (organization_id, source_table);

-- Rally26 fundraising approval workflow foundation.
-- Builds on V87 (feature/fundraising_templates). The owner controls whether a
-- non-owner fundraiser must be approved before it can become ACTIVE.

alter table campaign drop constraint campaign_status_check;
alter table campaign add constraint campaign_status_check
    check (status in ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'COMPLETED', 'ARCHIVED'));

alter table campaign add column submitted_at timestamptz;
alter table campaign add column approved_at timestamptz;
alter table campaign add column approved_by_user_id uuid references app_user (id);

create index campaign_pending_approval_idx
    on campaign (organization_id, submitted_at desc)
    where status = 'PENDING_APPROVAL';

-- Deliberately separate from user preferences: this is an organization policy,
-- not a per-user setting. No row means the safe default (owner approval required).
create table organization_fundraising_settings (
    organization_id uuid primary key references organization (id) on delete cascade,
    require_owner_approval boolean not null default true,
    updated_by_user_id uuid references app_user (id),
    updated_at timestamptz not null default now()
);

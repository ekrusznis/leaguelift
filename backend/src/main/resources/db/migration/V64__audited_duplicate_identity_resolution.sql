-- Phase 27.4: audited, preview-bound duplicate identity resolution.
-- Source app_user rows are retained for immutable historical attribution. A completed
-- account merge suspends the source and points it at the surviving target; it never
-- hard-deletes the source identity or rewrites audit_event.actor_user_id.

alter table app_user
    add column merged_into_user_id uuid references app_user (id),
    add column merged_at timestamptz;

alter table app_user
    add constraint app_user_not_merged_into_self_check check (merged_into_user_id is null or merged_into_user_id <> id),
    add constraint app_user_merge_state_check check (
        (merged_into_user_id is null and merged_at is null)
        or (merged_into_user_id is not null and merged_at is not null and status = 'SUSPENDED')
    );

create index app_user_merged_into_user_idx
    on app_user (merged_into_user_id)
    where merged_into_user_id is not null;

create table identity_resolution_operation (
    id                     uuid primary key default gen_random_uuid(),
    operation_type         text not null,
    status                 text not null default 'COMPLETED',
    source_kind            text not null,
    source_id              uuid not null,
    target_kind            text not null,
    target_id              uuid not null,
    organization_id        uuid not null references organization (id),
    platform_admin_user_id uuid not null references app_user (id),
    support_access_id      uuid not null references platform_support_access (id),
    reason                 text not null,
    preview_hash           text not null,
    outcome_json           jsonb not null default '{}'::jsonb,
    recovery_json          jsonb not null default '{}'::jsonb,
    created_at             timestamptz not null default now(),
    completed_at           timestamptz not null default now(),
    constraint identity_resolution_operation_type_check check (operation_type in ('LINK_GUARDIAN_SHELL', 'MERGE_APP_USERS')),
    constraint identity_resolution_operation_status_check check (status in ('COMPLETED', 'ROLLED_BACK')),
    constraint identity_resolution_source_kind_check check (source_kind in ('APP_USER', 'GUARDIAN_SHELL')),
    constraint identity_resolution_target_kind_check check (target_kind in ('APP_USER', 'GUARDIAN_SHELL')),
    constraint identity_resolution_operation_shape_check check (
        (operation_type = 'LINK_GUARDIAN_SHELL' and source_kind = 'GUARDIAN_SHELL' and target_kind = 'APP_USER')
        or (operation_type = 'MERGE_APP_USERS' and source_kind = 'APP_USER' and target_kind = 'APP_USER')
    ),
    constraint identity_resolution_reason_check check (char_length(trim(reason)) between 10 and 500),
    constraint identity_resolution_preview_hash_check check (preview_hash ~ '^[0-9a-f]{64}$'),
    constraint identity_resolution_different_identity_check check (source_kind <> target_kind or source_id <> target_id)
);

create unique index identity_resolution_one_completed_source_idx
    on identity_resolution_operation (source_kind, source_id)
    where status = 'COMPLETED';

create index identity_resolution_target_idx
    on identity_resolution_operation (target_kind, target_id, completed_at desc);

create index identity_resolution_organization_idx
    on identity_resolution_operation (organization_id, completed_at desc);

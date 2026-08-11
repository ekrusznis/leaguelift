-- Phase 31 slice 31.1 (DESIGN-DOC.md section 14.1L): athlete eligibility / guardian
-- e-sign waiver domain foundation. This slice is domain-model-only — versioned
-- requirement definitions, the accepted-evidence ledger, and a recomputed clearance
-- snapshot per participant/team. No native e-sign UI (slice 31.2), no external
-- provider evidence import (slice 31.3), no mobile guardian flow (slice 31.4) yet.
--
-- Design notes, matching the acceptance criteria this slice exists to satisfy:
--   - eligibility_requirement rows are IMMUTABLE once created. "Editing" a requirement
--     never updates an existing row's content/content_hash — it inserts a new row
--     sharing the same requirement_group_id with version = previous + 1 (acceptance
--     criterion #3/#8: prior signatures must never be silently reinterpreted against
--     changed document language). The old version is archived (status = 'ARCHIVED'),
--     not deleted, so historical evidence bound to it remains fully explainable.
--   - eligibility_evidence.requirement_id references the EXACT version a guardian
--     signed against — never the "current" version of a requirement family — for the
--     same reason.
--   - eligibility_clearance is a recomputed snapshot (participant_id, team_id) unique
--     pair, not a hand-editable status field, so "recompute deterministically and
--     preserve history" (#8) has no place to drift out of sync with the real evidence
--     ledger. The evidence/requirement tables are the source of truth; this table is a
--     cache the service layer overwrites wholesale on every recomputation.

create table eligibility_requirement (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    requirement_group_id        uuid not null,
    version                     integer not null,
    title                       text not null,
    mode                        text not null,
    content                     text not null,
    content_hash                text not null,
    sport                       text,
    program                     text,
    season                      text,
    team_id                     uuid references team (id),
    guardian_action_mode        text not null default 'ANY_ONE_GUARDIAN',
    athlete_self_sign_allowed   boolean not null default false,
    external_evidence_accepted  boolean not null default false,
    accepted_evidence_levels    jsonb not null default '[]'::jsonb,
    effective_start             date not null,
    effective_end               date,
    expiration_rule             text not null default 'NEVER',
    expiration_days             integer,
    status                      text not null default 'ACTIVE',
    created_at                  timestamptz not null default now(),
    created_by_user_id          uuid not null references app_user (id),
    constraint eligibility_requirement_group_version_unique unique (organization_id, requirement_group_id, version),
    constraint eligibility_requirement_mode_check check (mode in
        ('GUARDIAN_ESIGN_ACKNOWLEDGMENT', 'GUARDIAN_LEGAL_NAME_SIGNATURE', 'FILE_UPLOAD',
         'STAFF_REVIEWED_EXTERNAL', 'INFORMATIONAL')),
    constraint eligibility_requirement_guardian_action_mode_check check (guardian_action_mode in
        ('ANY_ONE_GUARDIAN', 'ALL_GUARDIANS')),
    constraint eligibility_requirement_expiration_rule_check check (expiration_rule in
        ('NEVER', 'SEASON_END', 'FIXED_DAYS_FROM_ACCEPTANCE', 'ORG_CONFIGURED')),
    constraint eligibility_requirement_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint eligibility_requirement_expiration_days_check check (
        (expiration_rule = 'FIXED_DAYS_FROM_ACCEPTANCE' and expiration_days is not null)
        or (expiration_rule <> 'FIXED_DAYS_FROM_ACCEPTANCE')
    )
);

create index eligibility_requirement_organization_id_idx on eligibility_requirement (organization_id, status);
create index eligibility_requirement_team_id_idx on eligibility_requirement (organization_id, team_id) where team_id is not null;
create index eligibility_requirement_group_id_idx on eligibility_requirement (organization_id, requirement_group_id);
-- At most one ACTIVE version per requirement family — the "current" version for
-- evaluation purposes. Older versions stay ARCHIVED, never deleted.
create unique index eligibility_requirement_one_active_per_group_idx on eligibility_requirement (organization_id, requirement_group_id)
    where status = 'ACTIVE';

create table eligibility_evidence (
    id                              uuid primary key default gen_random_uuid(),
    organization_id                 uuid not null references organization (id),
    participant_id                  uuid not null references participant (id),
    requirement_id                  uuid not null references eligibility_requirement (id),
    classification                  text not null,
    acceptance_method                text not null,
    signer_user_id                   uuid references app_user (id),
    signer_guardian_relationship     text,
    entered_legal_name               text,
    document_asset_id                uuid references media_asset (id),
    provider                         text,
    external_identifiers             jsonb,
    content_hash                     text,
    status                           text not null default 'ACTIVE',
    superseded_by_evidence_id        uuid references eligibility_evidence (id),
    accepted_at                      timestamptz not null default now(),
    expires_at                       timestamptz,
    ip_address                       text,
    user_agent                       text,
    reviewed_by_user_id               uuid references app_user (id),
    review_note                       text,
    created_at                       timestamptz not null default now(),
    updated_at                       timestamptz not null default now(),
    constraint eligibility_evidence_classification_check check (classification in
        ('VERIFIED_EXTERNAL_DOCUMENT', 'EXTERNAL_ACKNOWLEDGMENT', 'EXTERNAL_STATUS_ONLY',
         'MANUALLY_VERIFIED', 'MISSING', 'EXPIRED', 'REJECTED', 'REVOKED')),
    constraint eligibility_evidence_acceptance_method_check check (acceptance_method in
        ('ESIGN_LEGAL_NAME', 'ACKNOWLEDGMENT_CHECKBOX', 'FILE_UPLOAD', 'EXTERNAL_IMPORT',
         'STAFF_MANUAL_VERIFICATION')),
    constraint eligibility_evidence_status_check check (status in
        ('ACTIVE', 'SUPERSEDED', 'REVOKED', 'REJECTED', 'EXPIRED'))
);

create index eligibility_evidence_organization_id_idx on eligibility_evidence (organization_id, participant_id);
create index eligibility_evidence_requirement_id_idx on eligibility_evidence (requirement_id);
create index eligibility_evidence_participant_status_idx on eligibility_evidence (participant_id, status);

create table eligibility_clearance (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    participant_id              uuid not null references participant (id),
    team_id                     uuid not null references team (id),
    status                      text not null,
    unmet_requirement_count     integer not null default 0,
    computed_at                 timestamptz not null default now(),
    constraint eligibility_clearance_participant_team_unique unique (participant_id, team_id),
    constraint eligibility_clearance_status_check check (status in
        ('ROSTER_PENDING', 'DOCUMENTS_REQUIRED', 'UNDER_REVIEW', 'CLEARED', 'EXPIRED', 'INELIGIBLE'))
);

create index eligibility_clearance_organization_team_idx on eligibility_clearance (organization_id, team_id);

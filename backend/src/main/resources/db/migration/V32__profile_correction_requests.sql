create table profile_correction_request (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    household_id uuid not null references household(id),
    target_type varchar(32) not null,
    target_id uuid not null,
    field varchar(64) not null,
    target_label varchar(200) not null,
    current_value varchar(500),
    proposed_value varchar(500) not null,
    reason varchar(500) not null,
    status varchar(24) not null default 'PENDING',
    requested_by uuid not null references app_user(id),
    reviewed_by uuid references app_user(id),
    review_note varchar(500),
    requested_at timestamptz not null,
    reviewed_at timestamptz,
    updated_at timestamptz not null,
    constraint ck_profile_correction_target_type
        check (target_type in ('HOUSEHOLD_ADULT', 'PARTICIPANT')),
    constraint ck_profile_correction_field
        check (field in (
            'ADULT_FIRST_NAME', 'ADULT_LAST_NAME', 'ADULT_EMAIL', 'ADULT_PHONE', 'ADULT_RELATIONSHIP',
            'PARTICIPANT_FIRST_NAME', 'PARTICIPANT_LAST_NAME', 'PARTICIPANT_DATE_OF_BIRTH'
        )),
    constraint ck_profile_correction_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    constraint ck_profile_correction_target_field
        check (
            (target_type = 'HOUSEHOLD_ADULT' and field in (
                'ADULT_FIRST_NAME', 'ADULT_LAST_NAME', 'ADULT_EMAIL', 'ADULT_PHONE', 'ADULT_RELATIONSHIP'
            )) or
            (target_type = 'PARTICIPANT' and field in (
                'PARTICIPANT_FIRST_NAME', 'PARTICIPANT_LAST_NAME', 'PARTICIPANT_DATE_OF_BIRTH'
            ))
        ),
    constraint ck_profile_correction_review_state
        check (
            (status = 'PENDING' and reviewed_by is null and reviewed_at is null) or
            (status in ('APPROVED', 'REJECTED') and reviewed_by is not null and reviewed_at is not null) or
            (status = 'WITHDRAWN' and reviewed_by is null and reviewed_at is not null)
        )
);

create unique index uq_profile_correction_pending_target_field
    on profile_correction_request (organization_id, target_type, target_id, field)
    where status = 'PENDING';

create index ix_profile_correction_org_status_requested
    on profile_correction_request (organization_id, status, requested_at desc);

create index ix_profile_correction_household_requested
    on profile_correction_request (organization_id, household_id, requested_at desc);

create index ix_profile_correction_requester_requested
    on profile_correction_request (requested_by, requested_at desc);

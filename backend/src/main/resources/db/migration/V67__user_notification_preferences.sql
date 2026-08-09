create table user_notification_preference (
    user_id uuid not null references app_user(id) on delete cascade,
    topic text not null
        check (topic in (
            'EVENTS_SCHEDULE', 'RSVP', 'FEES_PAYMENTS', 'DOCUMENTS_ELIGIBILITY',
            'ANNOUNCEMENTS', 'FUNDRAISING', 'SWAG_SHOP_ORDERS', 'MESSAGES', 'SUPPORT'
        )),
    in_app_state text not null default 'DEFAULT'
        check (in_app_state in ('DEFAULT', 'ENABLED', 'DISABLED')),
    email_state text not null default 'DEFAULT'
        check (email_state in ('DEFAULT', 'ENABLED', 'DISABLED')),
    sms_state text not null default 'DEFAULT'
        check (sms_state in ('DEFAULT', 'ENABLED', 'DISABLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, topic)
);

create table user_sms_consent_event (
    id uuid primary key,
    user_id uuid not null references app_user(id) on delete cascade,
    state text not null check (state in ('GRANTED', 'REVOKED')),
    recorded_at timestamptz not null default now()
);

create index user_sms_consent_event_latest_idx
    on user_sms_consent_event (user_id, recorded_at desc, id desc);

comment on table user_notification_preference is
    'Typed optional per-user notification choices. Required transactional/security/financial/legal communications bypass these optional preferences.';
comment on table user_sms_consent_event is
    'Append-only individual SMS consent history. Household SMS flags never substitute for account-level consent.';

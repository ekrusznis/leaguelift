create table user_preference (
    user_id uuid primary key references app_user(id) on delete cascade,
    appearance text not null default 'SYSTEM'
        check (appearance in ('SYSTEM', 'LIGHT', 'DARK')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table user_preference is
    'Typed per-account UI preferences. Notification preferences remain a separate Phase 28.2 model.';

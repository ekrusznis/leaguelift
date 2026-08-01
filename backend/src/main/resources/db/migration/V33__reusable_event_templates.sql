create table event_template (
    id uuid primary key,
    organization_id uuid not null references organization(id),
    name varchar(120) not null,
    event_type varchar(20) not null check (event_type in ('COMPETITION', 'TOURNAMENT', 'PRACTICE', 'MEETING', 'OTHER')),
    title varchar(200),
    description varchar(2000),
    duration_minutes integer check (duration_minutes is null or duration_minutes between 1 and 1440),
    arrival_offset_minutes integer check (arrival_offset_minutes is null or arrival_offset_minutes between 0 and 1440),
    meeting_offset_minutes integer check (meeting_offset_minutes is null or meeting_offset_minutes between 0 and 1440),
    timezone varchar(100) not null,
    venue_name varchar(200),
    address varchar(300),
    area varchar(120),
    meeting_point varchar(300),
    directions_notes varchar(1000),
    visibility varchar(20) not null check (visibility in ('TEAM', 'ORGANIZATION', 'PUBLIC')),
    status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE', 'ARCHIVED')),
    created_by_user_id uuid not null references app_user(id),
    updated_by_user_id uuid not null references app_user(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index event_template_active_name_idx
    on event_template (organization_id, lower(name))
    where status = 'ACTIVE';

create index event_template_org_status_idx
    on event_template (organization_id, status, name);

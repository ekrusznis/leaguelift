-- Phase 17 help/support foundation (ADR-049).
-- Public/authenticated Help Center articles share one catalog. Support requests are
-- durable before any email is attempted, and retries are deduplicated by a caller-
-- supplied idempotency key.

create table support_article (
    id              uuid primary key default gen_random_uuid(),
    slug            text not null,
    title           text not null,
    summary         text not null,
    body_markdown   text not null,
    category        text not null,
    audience        text not null,
    status          text not null default 'DRAFT',
    sort_order      integer not null default 0,
    published_at    timestamptz,
    created_by      uuid references app_user (id),
    updated_by      uuid references app_user (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint support_article_slug_format_check check (slug ~ '^[a-z0-9]([a-z0-9-]{0,118}[a-z0-9])?$'),
    constraint support_article_title_check check (char_length(trim(title)) between 3 and 180),
    constraint support_article_summary_check check (char_length(trim(summary)) between 10 and 400),
    constraint support_article_body_check check (char_length(trim(body_markdown)) between 20 and 20000),
    constraint support_article_category_check check (char_length(trim(category)) between 3 and 80),
    constraint support_article_audience_check check (audience in ('PUBLIC', 'OWNER_ADMIN', 'COACH', 'GUARDIAN', 'ATHLETE', 'PLATFORM')),
    constraint support_article_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint support_article_sort_order_check check (sort_order between 0 and 10000),
    constraint support_article_published_at_check check (
        (status = 'PUBLISHED' and published_at is not null) or status <> 'PUBLISHED'
    )
);

create unique index support_article_slug_unique_idx on support_article (lower(slug));
create index support_article_public_lookup_idx on support_article (status, audience, category, sort_order, title);

create table support_case (
    id                         uuid primary key default gen_random_uuid(),
    idempotency_key            text not null,
    organization_id            uuid references organization (id),
    requester_user_id          uuid references app_user (id),
    requester_name             text not null,
    requester_email            text not null,
    category                   text not null,
    priority                   text not null default 'NORMAL',
    subject                    text not null,
    description                text not null,
    status                     text not null default 'OPEN',
    assigned_platform_user_id  uuid references app_user (id),
    resolution                 text,
    closed_at                  timestamptz,
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    constraint support_case_idempotency_key_check check (char_length(trim(idempotency_key)) between 8 and 120),
    constraint support_case_requester_name_check check (char_length(trim(requester_name)) between 2 and 120),
    constraint support_case_requester_email_check check (char_length(trim(requester_email)) between 3 and 320),
    constraint support_case_category_check check (category in (
        'ACCOUNT_ACCESS', 'ORGANIZATION_SETUP', 'TEAM_OR_ROSTER', 'FEES_OR_PAYMENTS',
        'FUNDRAISING', 'STORE_OR_ORDER', 'SPONSORSHIP', 'EVENT_OR_RSVP',
        'TECHNICAL_PROBLEM', 'PRIVACY_OR_SAFETY', 'OTHER'
    )),
    constraint support_case_priority_check check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    constraint support_case_subject_check check (char_length(trim(subject)) between 5 and 200),
    constraint support_case_description_check check (char_length(trim(description)) between 20 and 5000),
    constraint support_case_status_check check (status in ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED')),
    constraint support_case_resolution_check check (resolution is null or char_length(trim(resolution)) between 3 and 4000),
    constraint support_case_closed_at_check check (
        (status in ('RESOLVED', 'CLOSED') and closed_at is not null) or
        (status not in ('RESOLVED', 'CLOSED') and closed_at is null)
    )
);

create unique index support_case_idempotency_unique_idx on support_case (idempotency_key);
create index support_case_requester_idx on support_case (requester_user_id, created_at desc);
create index support_case_organization_idx on support_case (organization_id, created_at desc);
create index support_case_queue_idx on support_case (status, priority, created_at desc);

-- Small, accurate starter catalog so the Help Center is useful before a Platform
-- Administrator authors additional material. System-seeded rows deliberately have
-- null created_by/updated_by; all later authoring mutations are attributed and audited.
insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000001', 'getting-started-with-rally26',
     'Getting started with Rally26',
     'Understand the invitation-only account model and the first steps for an organization owner.',
     E'## Organization owners\n\nOnly an organization owner may create a new Rally26 organization through public registration. After verifying the owner email, the owner can set up organization details, teams, tournaments, households, participants, fees, pages, events, and other enabled modules.\n\n## Everyone else\n\nAdministrators, coaches, guardians, and permitted athletes receive organization-issued invitations. They do not create independent public accounts.',
     'Getting Started', 'PUBLIC', 'PUBLISHED', 10, now()),
    ('10000000-0000-4000-8000-000000000002', 'accounts-and-invitations',
     'Accounts and invitations',
     'How staff and guardian access is activated without public self-registration.',
     E'## Invitation links\n\nInvitation links are single-use and expire. The invited person confirms the invited email address, sets a password, and activates only the access already approved by the organization.\n\n## Expired or used links\n\nAsk an organization owner or administrator to resend the invitation. A resend rotates the token instead of reusing the old link.',
     'Accounts and Invitations', 'PUBLIC', 'PUBLISHED', 20, now()),
    ('10000000-0000-4000-8000-000000000003', 'fees-and-payments-overview',
     'Fees and payments overview',
     'Learn what households can view and what organization staff control.',
     E'Households can view assigned fees, due dates, payment history, adjustments, and outstanding balances for their own household. Organization staff record or manage payment activity according to their permissions. Rally26 fee credits are organization-approved fee reductions; they are not cash, transferable balances, or a bank account.',
     'Fees and Payments', 'PUBLIC', 'PUBLISHED', 30, now()),
    ('10000000-0000-4000-8000-000000000004', 'events-rsvp-and-calendars',
     'Events, RSVP, and calendars',
     'How schedules, RSVP responses, calendar files, and directions work.',
     E'Authorized staff can create and publish organization, team, and tournament events. Guardians and permitted athletes see only schedules they are authorized to access and can submit RSVP responses where enabled. Calendar downloads use standard ICS files, and directions links open an external maps service; Rally26 does not claim two-way Google Calendar synchronization.',
     'Events and RSVP', 'PUBLIC', 'PUBLISHED', 40, now()),
    ('10000000-0000-4000-8000-000000000005', 'privacy-and-youth-data',
     'Privacy and youth data',
     'Rally26 keeps participant records lightweight and adult-managed.',
     E'Rally26 is designed around organization-controlled access and adult-managed households. Participant records are lightweight and organization-private. The initial product does not collect medical, educational, behavioral, background-check, or precise-location records, and under-13 direct login remains disabled until a dedicated consent and privacy workflow is approved.',
     'Troubleshooting', 'PUBLIC', 'PUBLISHED', 50, now()),
    ('10000000-0000-4000-8000-000000000006', 'owner-organization-setup-checklist',
     'Organization setup checklist',
     'A practical setup order for owners and administrators.',
     E'1. Complete the organization profile and branding.\n2. Create teams and tournaments.\n3. Add staff invitations, households, guardians, and participants.\n4. Assign participants to teams.\n5. Configure fees, documents, events, public pages, fundraising, stores, or sponsorships that your organization will use.\n6. Review role access before inviting pilot families.',
     'Organization Setup', 'OWNER_ADMIN', 'PUBLISHED', 10, now()),
    ('10000000-0000-4000-8000-000000000007', 'coach-team-workspace-basics',
     'Coach and team workspace basics',
     'Understand team-scoped access for schedules, pages, rosters, and fundraising.',
     E'Coach access is team-scoped. The actions available in the workspace depend on the explicit team capability granted by the organization. A coach does not automatically receive access to unrelated teams, households, or organization-wide private financial information.',
     'Teams and Families', 'COACH', 'PUBLISHED', 10, now()),
    ('10000000-0000-4000-8000-000000000008', 'guardian-household-basics',
     'Guardian household basics',
     'Where guardians find participants, fees, documents, schedules, and correction requests.',
     E'Guardians can view only linked household information. Use the household workspace for participant details, fees and payment history, documents, schedules, RSVP, notification preferences, and permitted profile updates. Official team assignments require staff action or a supported correction workflow.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 10, now()),
    ('10000000-0000-4000-8000-000000000009', 'athlete-access-basics',
     'Athlete access basics',
     'What a permitted athlete account can and cannot access.',
     E'A permitted athlete account is linked to an existing participant record and receives limited self, team, schedule, RSVP, profile, guardian, and order access. It does not receive organization financial management access. Participants never self-register.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 10, now()),
    ('10000000-0000-4000-8000-000000000010', 'platform-support-access',
     'Platform support access',
     'How Rally26 employees enter a customer workspace for support.',
     E'Platform support access is reasoned, time-bounded, and scoped to one organization. The Rally26 employee remains the authenticated actor; the system does not silently impersonate the customer. Start and end activity is audited.',
     'Troubleshooting', 'PLATFORM', 'PUBLISHED', 10, now());

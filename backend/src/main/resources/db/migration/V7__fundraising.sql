-- Phase 3 slice 11: fundraising campaigns (DESIGN-DOC.md sections 8.5, 10.4, 35).
-- Campaigns own their own slug and draft/publish lifecycle directly (rather than
-- going through the generic public_page module used by organization/team/tournament
-- pages) because section 16.6's API surface gives campaigns a dedicated
-- POST .../campaigns/{campaignId}/publish endpoint, not the generic pages workflow.
--
-- Contribution/payment recording is intentionally NOT part of this migration —
-- section 16.6 only lists a checkout-session endpoint (real payment collection),
-- which belongs to slice 12 "test-mode payments". This slice is campaign
-- structure and its public page only, matching how fee_assignment (V6) shipped
-- with only a status field before any payment-recording mechanism existed.

create table campaign (
    id                uuid primary key default gen_random_uuid(),
    organization_id   uuid not null references organization (id),
    team_id           uuid references team (id),
    name              text not null,
    slug              text not null,
    description       text,
    campaign_type     text not null,
    goal_amount_minor bigint not null,
    currency          text not null default 'USD',
    start_date        date,
    end_date          date,
    status            text not null default 'DRAFT',
    published_at      timestamptz,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint campaign_status_check check (status in ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    constraint campaign_type_check check (campaign_type in (
        'ORGANIZATION_GENERAL', 'TEAM_GENERAL', 'TRAVEL', 'TOURNAMENT_FEES', 'UNIFORMS',
        'EQUIPMENT', 'FACILITY_IMPROVEMENTS', 'SCHOLARSHIPS', 'SPECIAL_EVENTS',
        'APPAREL_BASED', 'SPONSOR_SUPPORTED'
    )),
    constraint campaign_goal_check check (goal_amount_minor >= 0),
    constraint campaign_date_range_check check (end_date is null or start_date is null or end_date >= start_date)
);

create unique index campaign_slug_idx on campaign (lower(slug));
create index campaign_organization_id_idx on campaign (organization_id);
create index campaign_team_id_idx on campaign (team_id);

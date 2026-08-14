-- Phase 5A1: operational fundraiser lifecycle + payment-provider safety.
--
-- SCHEDULED = approved/activated but waiting for a future start_date.
-- ENDED     = end_date elapsed; no new contributions may be accepted.
-- CLOSED    = owner has completed fundraiser closeout/reconciliation.
-- COMPLETED remains accepted as a legacy state so existing rows/API clients remain readable.

alter table campaign drop constraint campaign_status_check;
alter table campaign
    add constraint campaign_status_check check (
        status in ('DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'ACTIVE', 'ENDED', 'CLOSED', 'COMPLETED', 'ARCHIVED')
    );

-- Provider-safety boundary. Ordinary fundraisers remain eligible for online Stripe
-- contribution checkout. Any fundraiser with a promotional/free-entry game is disabled
-- for Stripe contribution checkout at the campaign level. Offline/manual contribution
-- recording remains a separate organization-controlled workflow.
alter table campaign
    add column online_contributions_enabled boolean not null default true;

-- Forward-correct the legacy paid box-pool template and every already-created free game.
update campaign
set online_contributions_enabled = false,
    updated_at = now()
where template_key = 'BOX_POOL'
   or exists (select 1 from fundraising_game fg where fg.campaign_id = campaign.id);

-- Keep the safety rule true even if a new game is inserted outside the normal service layer.
create or replace function disable_campaign_online_contributions_for_game()
returns trigger
language plpgsql
as $$
begin
    update campaign
    set online_contributions_enabled = false,
        updated_at = now()
    where id = new.campaign_id;
    return new;
end;
$$;

create trigger fundraising_game_disable_online_contributions
    after insert on fundraising_game
    for each row
    execute function disable_campaign_online_contributions_for_game();

create index campaign_scheduled_start_idx
    on campaign(start_date)
    where status = 'SCHEDULED';

create index campaign_active_end_idx
    on campaign(end_date)
    where status = 'ACTIVE' and end_date is not null;

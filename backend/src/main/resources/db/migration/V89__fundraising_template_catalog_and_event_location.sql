-- Expand fundraiser templates and add optional location fields for in-person events.
-- Date scheduling continues to use campaign.start_date/end_date; presets are client UX only.

alter table campaign drop constraint campaign_template_key_check;
alter table campaign add constraint campaign_template_key_check
    check (
        template_key is null or template_key in (
            'GENERAL',
            'IN_PERSON_EVENT',
            'SPONSOR_MATCH',
            'MILESTONE_CHALLENGE',
            'FUNDRAISING_CHALLENGE',
            'BOX_POOL',
            'BAKE_SALE',
            'CAR_WASH'
        )
    );

alter table campaign add column event_location_name text;
alter table campaign add column event_address text;

alter table campaign add constraint campaign_event_location_name_length_check
    check (event_location_name is null or char_length(event_location_name) <= 160);
alter table campaign add constraint campaign_event_address_length_check
    check (event_address is null or char_length(event_address) <= 500);

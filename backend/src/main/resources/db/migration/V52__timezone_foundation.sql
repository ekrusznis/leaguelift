alter table organization
    add column address_line1       text,
    add column address_line2       text,
    add column address_city        text,
    add column address_state       text,
    add column address_postal_code text,
    add column address_country     text,
    add column timezone            text;

alter table organization
    add constraint organization_timezone_format_check
    check (timezone is null or timezone ~ '^[A-Za-z0-9_+\-]+(/[A-Za-z0-9_+\-]+)*$');

alter table team add column timezone_override text;
alter table tournament add column timezone_override text;

alter table event add column all_day_date date;
alter table event
    add constraint event_all_day_excludes_start_at_check
    check (all_day_date is null or start_at is null);

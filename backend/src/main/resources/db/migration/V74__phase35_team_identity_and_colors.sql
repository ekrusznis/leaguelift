-- Phase 35 (ADR-099): structured team identity (age group, gender category, level) and
-- team colors. `team` today has only free-text name/sport, so age/gender/level distinctions
-- live entirely inside an opaque name string, and `team_org_name_key` (V3) collides when a
-- club wants two same-named teams at different ages (e.g. two "Pirates" squads). No existing
-- row has any of these values (no seed data inserts team rows), so no backfill is required —
-- every new column defaults to null, which is a safe degenerate case of the widened constraint.

alter table team
    add column age_group       text,
    add column gender_category text,
    add column level           text,
    add column primary_color   char(7),
    add column secondary_color char(7);

alter table team
    add constraint team_gender_category_check check (
        gender_category is null or gender_category in ('BOYS', 'GIRLS', 'COED', 'MENS', 'WOMENS', 'OPEN')
    ),
    add constraint team_primary_color_check check (primary_color is null or primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    add constraint team_secondary_color_check check (secondary_color is null or secondary_color ~ '^#[0-9A-Fa-f]{6}$');

alter table team drop constraint team_org_name_key;
alter table team add constraint team_org_identity_key
    unique (organization_id, sport, age_group, gender_category, level, name);

create index team_org_filter_idx on team (organization_id, sport, age_group, gender_category);

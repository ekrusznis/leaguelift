-- team.sport becomes a validated, canonical code instead of unconstrained free text
-- (mirrors the gender_category pattern from V74: a text column + check constraint, not
-- a real Postgres enum type, so adding a sport later is a plain migration, not a type
-- alteration). Existing real values ("Soccer", "Basketball") are normalized to their
-- canonical codes; anything else falls back to OTHER with the original text preserved
-- in the new sport_other_label column so no team's real sport name is silently lost.
update team set sport = 'SOCCER' where lower(sport) = 'soccer';
update team set sport = 'BASKETBALL' where lower(sport) = 'basketball';

alter table team add column sport_other_label text;

update team
set sport_other_label = sport,
    sport = 'OTHER'
where sport not in ('SOCCER', 'BASKETBALL');

alter table team
    add constraint team_sport_check check (
        sport in (
            'SOCCER', 'BASKETBALL', 'BASEBALL', 'SOFTBALL', 'FOOTBALL', 'ICE_HOCKEY', 'FIELD_HOCKEY',
            'VOLLEYBALL', 'LACROSSE', 'SWIMMING', 'TRACK_AND_FIELD', 'CROSS_COUNTRY', 'TENNIS',
            'WRESTLING', 'CHEERLEADING', 'GYMNASTICS', 'GOLF', 'RUGBY', 'OTHER'
        )
    );

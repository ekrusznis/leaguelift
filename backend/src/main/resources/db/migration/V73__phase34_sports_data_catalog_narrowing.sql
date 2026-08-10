-- Phase 34 (ADR-098): narrow the SPORTS_DATA provider catalog to the two providers with a
-- real, verifiable API path. Remove MAXPREPS (no public developer API or partner program
-- exists) and GAMECHANGER (no public API/OAuth/webhooks; only a manual CSV/PDF export a
-- coach can download today) as connectable catalog entries. Add TEAMSNAP, which publishes
-- a real self-serve OAuth2 API, at the same NOT_CONFIGURED/OAUTH_SCAFFOLD readiness
-- SportsEngine already uses pending a registered and verified Rally26 developer application.

-- Defensive cleanup: no connect flow was ever wired for GAMECHANGER/MAXPREPS (both were
-- FILE_IMPORT/PARTNER_PENDING, never OAUTH_SCAFFOLD), so no integration_connection rows can
-- reference them. The only scaffold that could have produced provider-scoped rows for either
-- is the (now-retired) file-import preview path, so clear any such rows before the catalog
-- delete to satisfy the FK from sports_data_mapping/sports_data_import_run.
delete from sports_data_import_issue
    where import_run_id in (
        select id from sports_data_import_run where provider in ('GAMECHANGER', 'MAXPREPS')
    );
delete from sports_data_mapping where provider in ('GAMECHANGER', 'MAXPREPS');
delete from sports_data_import_run where provider in ('GAMECHANGER', 'MAXPREPS');

alter table sports_data_mapping drop constraint sports_data_provider_check;
alter table sports_data_mapping add constraint sports_data_provider_check check (provider in ('SPORTSENGINE', 'TEAMSNAP'));

alter table sports_data_import_run drop constraint sports_import_provider_check;
alter table sports_data_import_run add constraint sports_import_provider_check check (provider in ('SPORTSENGINE', 'TEAMSNAP'));

delete from integration_provider_catalog where provider in ('GAMECHANGER', 'MAXPREPS');

insert into integration_provider_catalog
    (provider, display_name, category, ownership_scope, primary_auth_mode, supported_auth_modes,
     baseline_readiness, adapter_mode, description, activation_requirement, default_scopes, sort_order, visible_to_customers)
values
    ('TEAMSNAP', 'TeamSnap', 'SPORTS_DATA', 'ORGANIZATION', 'OAUTH2', '["OAUTH2"]',
     'NOT_CONFIGURED', 'OAUTH_SCAFFOLD',
     'Organization, team, roster, and schedule mapping scaffold pending a registered and verified Rally26 developer application.',
     'Requires a registered TeamSnap developer application, approved OAuth2 scopes, and credentialed verification.',
     '[]', 40, true);

-- Retire the GameChanger/MaxPreps article; both providers are gone from the catalog and
-- GameChanger never had a real connect flow to explain readiness for. Add a TeamSnap
-- readiness article modeled on the existing SportsEngine one.
update support_article
set slug = 'gamechanger-csv-import',
    title = 'Using GameChanger data with Rally26',
    summary = 'GameChanger has no public API or partner program, so this describes the one real supported path: CSV export and import.',
    body_markdown = E'## No direct connection\n\nGameChanger has no public developer API, OAuth, or webhooks today, so Rally26 does not offer a "Connect" card for it. MaxPreps has no public developer API or partner program either and is not offered as an integration at all.\n\n## Supported path\n\nExport a CSV from GameChanger, then use Rally26''s existing reviewed CSV event import to bring that schedule data in with duplicate protection and a preview before anything is created.\n\n## Future activation\n\nIf GameChanger publishes an official partner or export contract in the future, a dedicated column-mapped importer built against a real sample export is a possible later phase.',
    category = 'Events and RSVP',
    sort_order = 60,
    published_at = now()
where id = '10000000-0000-4000-8000-000000000020';

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000022', 'teamsnap-integration-readiness',
     'TeamSnap integration readiness',
     'How TeamSnap connection, mapping, previews, and activation will work.',
     E'## Current status\n\nTeamSnap remains **Not configured** until Rally26 registers a developer application and verifies scopes against TeamSnap''s public OAuth2 API.\n\n## Planned workflow\n\nAn owner or administrator authorizes the organization, reviews mapped organizations, teams, participants, roster memberships, and events, resolves conflicts, and confirms an idempotent import. Every imported record retains provider and external identity.\n\n## Safety\n\nThis scaffold provides preview and mapping contracts only. It does not silently create or overwrite Rally26 records and it never asks for an undocumented token.',
     'Organization Setup', 'OWNER_ADMIN', 'PUBLISHED', 62, now());

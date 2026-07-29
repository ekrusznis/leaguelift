-- Dev-only fixtures exercising the Phase 7 capability model (DESIGN-DOC.md section 4.2,
-- ADR-020) end-to-end: a real team-scoped coach grant, a real guardian_relationship, a
-- real athlete self-link, a tournament + tournament-admin grant, and a platform admin.
-- Same rules as V9000: local-only (db/seed/, never loaded in staging/prod), same
-- shared dev password "DevPassword123!" for every new account below.
--
-- V9001+ follows V9000's own numbering convention for this location.

-- Coach Jordan Ellis (already created by V9000, id ...011) is granted a real,
-- team-scoped TEAM_MANAGER role on Varsity Soccer (...002) — replacing the "not
-- actually scoped to just Varsity Soccer" org-wide TEAM_ADMINISTRATOR reliance V9000's
-- own comment flagged.
insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status, granted_by)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000011',
    'TEAM',
    '00000000-0000-0000-0000-000000000002',
    'TEAM_MANAGER',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000010'
);

-- Real guardian_relationship linking Sarah Johnson's app_user (...012) to her own
-- household_adult record (...021) — the FK-backed replacement for V9000's
-- email-matching-only correlation.
insert into guardian_relationship (organization_id, household_id, household_adult_id, user_id)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000021',
    '00000000-0000-0000-0000-000000000012'
);

-- Real athlete self-link: Maya's app_user (...013, V9000's "controlled test account")
-- is now formally linked to her own participant record (...022), granted by her
-- guardian Sarah (...012) — see AuthorizationService.linkAthleteSelf's class doc.
insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status, granted_by)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000013',
    'PARTICIPANT',
    '00000000-0000-0000-0000-000000000022',
    'ATHLETE_SELF',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000012'
);

-- Tournament fixture + a tournament-only administrator account (no organization
-- membership row at all — exercises DashboardContextService's role_assignment(TOURNAMENT)
-- routing path with no organization_membership present).
insert into tournament (id, organization_id, name, sport, status, start_date, end_date, location)
values (
    '00000000-0000-0000-0000-000000000030',
    '00000000-0000-0000-0000-000000000001',
    'Riverside Fall Classic',
    'Soccer',
    'ACTIVE',
    date '2026-09-12',
    date '2026-09-13',
    'Riverside Sports Complex'
);

insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000014',
    'taylor.reed@riversideyouthsports.example',
    'Taylor Reed',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status, granted_by)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000014',
    'TOURNAMENT',
    '00000000-0000-0000-0000-000000000030',
    'TOURNAMENT_ADMINISTRATOR',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000010'
);

-- Platform administrator account. Until this migration, no account could ever reach
-- the Platform Admin dashboard — CurrentUser.platformAdministrator was hardcoded false
-- everywhere (see JwtCurrentUserConverter before Phase 7/ADR-020).
insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000015',
    'platform.admin@leaguelift.example',
    'Priya Shah',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status, granted_by)
values (
    null,
    '00000000-0000-0000-0000-000000000015',
    'PLATFORM',
    null,
    'PLATFORM_ADMINISTRATOR',
    'ACTIVE',
    null
);

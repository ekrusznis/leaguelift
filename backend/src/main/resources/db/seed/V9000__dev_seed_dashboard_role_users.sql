-- Dev-only fixture data for exercising the four dashboard roles described in
-- docs/RALLY26_DASHBOARD_DESIGN.md (athlete, parent/guardian, coach, owner)
-- once real authentication is wired up. This file lives in db/seed/, a Flyway
-- location added to spring.flyway.locations ONLY in application-local.yml — it
-- never runs against staging or prod (see ADR-003; a versioned migration under
-- db/migration/ is never removed or made environment-conditional, so seed data
-- that must not ship to real environments lives in its own location instead).
--
-- Version numbering: this location shares one Flyway schema_history table with
-- db/migration/ when both are on the classpath, so version numbers must not
-- collide. V9000+ is reserved for dev-seed-only migrations, keeping it well
-- clear of the real schema sequence (currently V1-V6). Add further dev seed
-- files as V9001, V9002, etc.
--
-- Known limitation: today's schema (V1-V6) has no tables linking an app_user to
-- a household_adult or participant record — guardian_relationship and
-- user_person_link (dashboard design doc section 19.1/19.3) don't exist yet.
-- The parent and athlete app_user rows below are therefore only correlated to
-- their household/participant fixtures BY MATCHING EMAIL/NAME, as a convenience
-- for whoever wires that link up next — there is no foreign key enforcing it.
-- The owner and coach rows, by contrast, use the real organization_membership
-- table that already exists today.
--
-- All four accounts below share the same dev-only password: "DevPassword123!"
-- (bcrypt hash below), so local sign-in against POST /api/v1/auth/login can be
-- exercised for any of them without a real registration flow.
--
-- NOTE: editing this file's checksum after it has already been applied to a local
-- database requires `./gradlew flywayRepair` (or dropping/recreating the local db)
-- before Flyway will accept it again — expected for a dev-only fixture, per this
-- file's own versioning note above.

-- Organization: Riverside Youth Sports Club (also referenced by the dashboard
-- mock designs' "Riverside High School" teams).
insert into organization (id, name, slug, organization_type, status)
values (
    '00000000-0000-0000-0000-000000000001',
    'Riverside Youth Sports Club',
    'riverside-youth-sports-club',
    'TRAVEL_CLUB',
    'ACTIVE'
);

insert into team (id, organization_id, name, sport, season, status)
values (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Varsity Soccer',
    'Soccer',
    '2024-2025',
    'ACTIVE'
);

-- Owner / Director test account — fully wired through the real
-- organization_membership table.
insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000010',
    'mike.anderson@riversideyouthsports.example',
    'Mike Anderson',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

insert into organization_membership (organization_id, user_id, role, status)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000010',
    'OWNER',
    'ACTIVE'
);

-- Coach test account. There is no per-team role-assignment table yet (dashboard
-- design doc section 6.1/19.2 "role_assignment" is future work), so this uses
-- the closest capability that exists today: the org-scoped TEAM_ADMINISTRATOR
-- membership role. It is not actually scoped to just Varsity Soccer.
insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000011',
    'jordan.ellis@riversideyouthsports.example',
    'Jordan Ellis',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

insert into organization_membership (organization_id, user_id, role, status)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000011',
    'TEAM_ADMINISTRATOR',
    'ACTIVE'
);

-- Household, guardian contact, and athlete participant fixtures.
insert into household (id, organization_id, display_name, contact_email, contact_phone, status)
values (
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000001',
    'Johnson Family',
    'sarah.johnson@example.com',
    '555-0198',
    'ACTIVE'
);

insert into household_adult (id, household_id, organization_id, first_name, last_name, email, phone, relationship, is_primary, status)
values (
    '00000000-0000-0000-0000-000000000021',
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000001',
    'Sarah',
    'Johnson',
    'sarah.johnson@example.com',
    '555-0198',
    'Parent',
    true,
    'ACTIVE'
);

insert into participant (id, household_id, organization_id, first_name, last_name, date_of_birth, status)
values (
    '00000000-0000-0000-0000-000000000022',
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000001',
    'Maya',
    'Johnson',
    date '2009-04-12',
    'ACTIVE'
);

insert into participant_team (participant_id, team_id, organization_id, status, joined_at)
values (
    '00000000-0000-0000-0000-000000000022',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'ACTIVE',
    date '2024-08-01'
);

-- Parent/guardian test account (correlated to household_adult above by email only).
insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000012',
    'sarah.johnson@example.com',
    'Sarah Johnson',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

-- Athlete test account — a "controlled test account" per dashboard design doc
-- section 5.2, not a standard product account. Real under-13 athletes do not
-- get independent Rally26 accounts; this exists solely so the athlete
-- dashboard can be exercised end-to-end once auth is wired up. Correlated to
-- the participant row above by name only.
insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000013',
    'maya.johnson@example.com',
    'Maya Johnson',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

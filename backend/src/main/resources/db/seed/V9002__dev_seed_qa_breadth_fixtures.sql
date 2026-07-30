-- Dev-only fixtures extending V9000/V9001's single-org, single-household baseline
-- with enough breadth to actually verify calculations, not just "does it render"
-- (Phase 7 completion demo-data audit). Same rules as V9000/V9001: local-only
-- (db/seed/, never loaded in staging/prod), shared dev password
-- "DevPassword123!" for every new account below, V9002 continues V9000/V9001's
-- own numbering convention for this location.
--
-- What this adds, with known-correct expected values for QA to check against:
--   - A second team (JV Soccer) on the existing Riverside org, for coach
--     multi-team-selector testing (CoachDashboard's team selector, task #3).
--   - A second household (Martinez Family) on Riverside, with its own adult +
--     participant, for search/list breadth and an OPEN (fully unpaid) fee case.
--   - Fee assignments across all three of OPEN/PARTIALLY_PAID/PAID so
--     FeeRepository.getFinancialSummary's real math is exercised:
--       Riverside org totals -> feesAssignedMinor = 40000, feesCollectedMinor = 19000,
--       outstandingMinor = 20000 (see the two per-assignment comments below for the
--       per-row math this rolls up from).
--   - A published fundraising campaign + one confirmed contribution with matching
--     ledger entries (CONTRIBUTION/LEAGUELIFT_PLATFORM_FEE/ORGANIZATION_EARNING),
--     so both the campaign's raisedMinor query and the ledger-based Financial
--     Overview/Reports Snapshot/Payout Summary numbers (Phase 7 completion demo-data
--     audit) have real, checkable data instead of just zero.
--   - A second organization (Lakeside Sports Alliance) with its own owner/team/
--     household, so platform-wide aggregates (Platform Admin dashboard,
--     Phase 7 completion) sum across more than one organization, and org-scoped
--     Global Search (Phase 7 completion) can be verified to NOT leak Riverside
--     results into a Lakeside-scoped search or vice versa.
--
-- Deliberately NOT added here: store/product/order and sponsorship fixtures —
-- those chains (product variants, Printify cost snapshots, checkout sessions) have
-- enough schema surface of their own that seeding them accurately deserves its own
-- follow-up pass rather than guessing at values here (DESIGN-DOC.md section 20.1:
-- build only what the active milestone needs).

-- --- Riverside: second team, for coach multi-team-selector testing ---

insert into team (id, organization_id, name, sport, season, status)
values (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'JV Soccer',
    'Soccer',
    '2024-2025',
    'ACTIVE'
);

-- Coach Jordan Ellis (V9000/V9001) also manages JV Soccer, not just Varsity —
-- exercises CoachDashboard's team selector (task #3) actually having 2 real options.
insert into role_assignment (organization_id, user_id, context_type, resource_id, role, status, granted_by)
values (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000011',
    'TEAM',
    '00000000-0000-0000-0000-000000000003',
    'TEAM_MANAGER',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000010'
);

-- --- Riverside: second household, its own adult + participant on JV Soccer ---

insert into household (id, organization_id, display_name, contact_email, contact_phone, status)
values (
    '00000000-0000-0000-0000-000000000023',
    '00000000-0000-0000-0000-000000000001',
    'Martinez Family',
    'carlos.martinez@example.com',
    '555-0177',
    'ACTIVE'
);

insert into household_adult (id, household_id, organization_id, first_name, last_name, email, phone, relationship, is_primary, status)
values (
    '00000000-0000-0000-0000-000000000024',
    '00000000-0000-0000-0000-000000000023',
    '00000000-0000-0000-0000-000000000001',
    'Carlos',
    'Martinez',
    'carlos.martinez@example.com',
    '555-0177',
    'Parent',
    true,
    'ACTIVE'
);

insert into participant (id, household_id, organization_id, first_name, last_name, date_of_birth, status)
values (
    '00000000-0000-0000-0000-000000000025',
    '00000000-0000-0000-0000-000000000023',
    '00000000-0000-0000-0000-000000000001',
    'Sofia',
    'Martinez',
    date '2010-02-20',
    'ACTIVE'
);

insert into participant_team (participant_id, team_id, organization_id, status, joined_at)
values (
    '00000000-0000-0000-0000-000000000025',
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'ACTIVE',
    date '2024-08-15'
);

-- --- Fee templates + assignments in every status, with known-correct balances ---

insert into fee_template (id, organization_id, name, description, amount_minor, currency, status)
values (
    '00000000-0000-0000-0000-000000000040',
    '00000000-0000-0000-0000-000000000001',
    'Registration Fee',
    'Season registration',
    15000,
    'USD',
    'ACTIVE'
);

-- Maya Johnson: Registration Fee, $150.00, paid in full -> status PAID, balance $0.
insert into fee_assignment (id, organization_id, household_id, participant_id, fee_template_id, description, original_amount_minor, currency, due_date, status)
values (
    '00000000-0000-0000-0000-000000000041',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000022',
    '00000000-0000-0000-0000-000000000040',
    'Registration Fee',
    15000,
    'USD',
    date '2026-08-15',
    'PAID'
);

insert into fee_payment (id, organization_id, fee_assignment_id, household_id, amount_minor, currency, method, paid_at, recorded_by_user_id)
values (
    '00000000-0000-0000-0000-000000000042',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000041',
    '00000000-0000-0000-0000-000000000020',
    15000,
    'USD',
    'CASH',
    date '2026-07-01',
    '00000000-0000-0000-0000-000000000010'
);

-- Maya Johnson: Uniform Fee, $100.00 original, $40.00 paid + $10.00 discount
-- adjustment -> status PARTIALLY_PAID, balance = 100 - 40 - 10 = $50.00.
insert into fee_assignment (id, organization_id, household_id, participant_id, fee_template_id, description, original_amount_minor, currency, due_date, status)
values (
    '00000000-0000-0000-0000-000000000043',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000022',
    null,
    'Uniform Fee',
    10000,
    'USD',
    date '2026-09-01',
    'PARTIALLY_PAID'
);

insert into fee_payment (id, organization_id, fee_assignment_id, household_id, amount_minor, currency, method, paid_at, recorded_by_user_id)
values (
    '00000000-0000-0000-0000-000000000044',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000043',
    '00000000-0000-0000-0000-000000000020',
    4000,
    'USD',
    'VENMO',
    date '2026-08-05',
    '00000000-0000-0000-0000-000000000010'
);

insert into fee_adjustment (id, organization_id, fee_assignment_id, household_id, adjustment_type, amount_minor, currency, reason, created_by_user_id)
values (
    '00000000-0000-0000-0000-000000000045',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000043',
    '00000000-0000-0000-0000-000000000020',
    'DISCOUNT',
    1000,
    'USD',
    'Sibling discount',
    '00000000-0000-0000-0000-000000000010'
);

-- Sofia Martinez: Registration Fee, $150.00, untouched -> status OPEN, balance $150.00.
insert into fee_assignment (id, organization_id, household_id, participant_id, fee_template_id, description, original_amount_minor, currency, due_date, status)
values (
    '00000000-0000-0000-0000-000000000046',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000023',
    '00000000-0000-0000-0000-000000000025',
    '00000000-0000-0000-0000-000000000040',
    'Registration Fee',
    15000,
    'USD',
    date '2026-08-15',
    'OPEN'
);

-- --- Fundraising campaign + confirmed contribution + matching ledger entries ---
-- 5% platform fee (ADR-017's configurable default): $250.00 contribution ->
-- $12.50 platform fee, $237.50 organization earning.

insert into campaign (id, organization_id, team_id, name, slug, description, campaign_type, goal_amount_minor, currency, start_date, end_date, status, published_at)
values (
    '00000000-0000-0000-0000-000000000050',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    'Spring Trip Fund',
    'spring-trip-fund',
    'Help send the team to the spring tournament.',
    'TRAVEL',
    200000,
    'USD',
    date '2026-03-01',
    date '2026-05-31',
    'ACTIVE',
    now()
);

insert into contribution (id, organization_id, campaign_id, amount_minor, currency, supporter_name, is_anonymous, supporter_email, status, confirmed_at)
values (
    '00000000-0000-0000-0000-000000000051',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000050',
    25000,
    'USD',
    'Grandma Johnson',
    false,
    'grandma.johnson@example.com',
    'CONFIRMED',
    now()
);

insert into ledger_entry (id, organization_id, account_code, entry_type, direction, amount_minor, currency, source_type, source_id, description, effective_at)
values (
    '00000000-0000-0000-0000-000000000052',
    '00000000-0000-0000-0000-000000000001',
    'CONTRIBUTION',
    'CONTRIBUTION',
    'CREDIT',
    25000,
    'USD',
    'CONTRIBUTION',
    '00000000-0000-0000-0000-000000000051',
    'Spring Trip Fund contribution from Grandma Johnson',
    now()
);

insert into ledger_entry (id, organization_id, account_code, entry_type, direction, amount_minor, currency, source_type, source_id, description, effective_at)
values (
    '00000000-0000-0000-0000-000000000053',
    '00000000-0000-0000-0000-000000000001',
    'LEAGUELIFT_PLATFORM_FEE',
    'LEAGUELIFT_PLATFORM_FEE',
    'DEBIT',
    1250,
    'USD',
    'CONTRIBUTION',
    '00000000-0000-0000-0000-000000000051',
    'Platform fee (5%) on Spring Trip Fund contribution',
    now()
);

insert into ledger_entry (id, organization_id, account_code, entry_type, direction, amount_minor, currency, source_type, source_id, description, effective_at)
values (
    '00000000-0000-0000-0000-000000000054',
    '00000000-0000-0000-0000-000000000001',
    'ORGANIZATION_EARNING',
    'ORGANIZATION_EARNING',
    'CREDIT',
    23750,
    'USD',
    'CONTRIBUTION',
    '00000000-0000-0000-0000-000000000051',
    'Net organization earning on Spring Trip Fund contribution',
    now()
);

-- --- A second organization, for platform-wide/cross-org breadth ---

insert into organization (id, name, slug, organization_type, status)
values (
    '00000000-0000-0000-0000-000000000060',
    'Lakeside Sports Alliance',
    'lakeside-sports-alliance',
    'RECREATIONAL_LEAGUE',
    'ACTIVE'
);

insert into team (id, organization_id, name, sport, season, status)
values (
    '00000000-0000-0000-0000-000000000061',
    '00000000-0000-0000-0000-000000000060',
    'Lakeside Basketball',
    'Basketball',
    '2024-2025',
    'ACTIVE'
);

insert into app_user (id, email, display_name, status, password_hash)
values (
    '00000000-0000-0000-0000-000000000062',
    'devon.park@lakesidesports.example',
    'Devon Park',
    'ACTIVE',
    '$2b$10$BC19Z63oXHKHirkZ18mYne4CETqhLd8m3yCb.pn7ob5GL7T91vhGu'
);

insert into organization_membership (organization_id, user_id, role, status)
values (
    '00000000-0000-0000-0000-000000000060',
    '00000000-0000-0000-0000-000000000062',
    'OWNER',
    'ACTIVE'
);

insert into household (id, organization_id, display_name, contact_email, contact_phone, status)
values (
    '00000000-0000-0000-0000-000000000063',
    '00000000-0000-0000-0000-000000000060',
    'Nguyen Family',
    'linh.nguyen@example.com',
    '555-0142',
    'ACTIVE'
);

insert into household_adult (id, household_id, organization_id, first_name, last_name, email, phone, relationship, is_primary, status)
values (
    '00000000-0000-0000-0000-000000000064',
    '00000000-0000-0000-0000-000000000063',
    '00000000-0000-0000-0000-000000000060',
    'Linh',
    'Nguyen',
    'linh.nguyen@example.com',
    '555-0142',
    'Parent',
    true,
    'ACTIVE'
);

insert into participant (id, household_id, organization_id, first_name, last_name, date_of_birth, status)
values (
    '00000000-0000-0000-0000-000000000065',
    '00000000-0000-0000-0000-000000000063',
    '00000000-0000-0000-0000-000000000060',
    'Ethan',
    'Nguyen',
    date '2011-06-30',
    'ACTIVE'
);

insert into participant_team (participant_id, team_id, organization_id, status, joined_at)
values (
    '00000000-0000-0000-0000-000000000065',
    '00000000-0000-0000-0000-000000000061',
    '00000000-0000-0000-0000-000000000060',
    'ACTIVE',
    date '2024-09-01'
);

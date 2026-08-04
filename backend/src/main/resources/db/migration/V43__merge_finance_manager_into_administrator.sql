-- Merges the FINANCE_MANAGER organization role into ADMINISTRATOR (ADR-060). The role
-- had drifted into a real capability bug: Capabilities.kt granted it the exact same
-- organization-level access as VIEWER, contradicting its documented "real financial
-- operations authority" (refunds, offline records, reconciliation), which is in fact
-- already gated to OWNER/ADMINISTRATOR only via MembershipService.requireManagerRole.
-- Rather than build out a new, narrower capability set for a still-hypothetical
-- distinct role, the founder's call was to stop pretending it's a separate role at all
-- — anyone doing finance-manager-shaped work is an Administrator.
--
-- Order matters: convert any existing rows BEFORE narrowing the check constraint, so
-- this is safe to run against a database that already has real FINANCE_MANAGER members.

update organization_membership
set role = 'ADMINISTRATOR', updated_at = now()
where role = 'FINANCE_MANAGER';

-- Every invitation row (not just PENDING) must satisfy the narrowed constraint below,
-- so historical ACCEPTED/REVOKED/EXPIRED rows are converted too, not just live ones.
update invitation
set role = 'ADMINISTRATOR', updated_at = now()
where role = 'FINANCE_MANAGER';

alter table organization_membership drop constraint organization_membership_role_check;
alter table organization_membership add constraint organization_membership_role_check check (role in (
    'OWNER', 'ADMINISTRATOR', 'TEAM_ADMINISTRATOR',
    'TOURNAMENT_ADMINISTRATOR', 'VIEWER'
));

alter table invitation drop constraint invitation_role_check;
alter table invitation add constraint invitation_role_check check (role in (
    'ADMINISTRATOR', 'TEAM_ADMINISTRATOR',
    'TOURNAMENT_ADMINISTRATOR', 'VIEWER'
));

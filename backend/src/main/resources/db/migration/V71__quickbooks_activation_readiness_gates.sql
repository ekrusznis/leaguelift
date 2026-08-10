-- Phase 29.4 / ADR-095: truthful QuickBooks activation-readiness gates.
-- This migration does not activate QuickBooks and does not enable provider writes.
-- Future credentialed phases may populate the approval timestamps only through audited workflows.

alter table quickbooks_connection_setting
    add column last_mapping_validation_at timestamptz,
    add column last_mapping_validation_status text not null default 'NOT_RUN',
    add column credential_verified_at timestamptz,
    add column sandbox_verified_at timestamptz,
    add column accounting_approved_at timestamptz,
    add column write_policy_approved_at timestamptz,
    add column write_policy_version text;

alter table quickbooks_connection_setting
    add constraint quickbooks_mapping_validation_status_check
        check (last_mapping_validation_status in ('NOT_RUN', 'PASSED', 'NEEDS_ATTENTION'));

alter table quickbooks_connection_setting
    add constraint quickbooks_write_policy_approval_pair_check
        check (
            (write_policy_approved_at is null and write_policy_version is null)
            or
            (write_policy_approved_at is not null and nullif(btrim(write_policy_version), '') is not null)
        );

comment on column quickbooks_connection_setting.credential_verified_at is
    'Future audited proof that real Intuit credentials/scopes were verified; never inferred from realm metadata or a local connection record.';

comment on column quickbooks_connection_setting.sandbox_verified_at is
    'Future audited proof that the integration passed end-to-end QuickBooks sandbox verification.';

comment on column quickbooks_connection_setting.accounting_approved_at is
    'Future explicit owner/accountant approval of the accounting mapping/posting policy.';

comment on column quickbooks_connection_setting.write_policy_approved_at is
    'Future explicit approval of a versioned provider-write policy. Phase 29 leaves this null.';

comment on column quickbooks_connection_setting.write_policy_version is
    'Version identifier for the separately approved provider-write policy; null throughout Phase 29.';

-- Phase 29.2 / ADR-093: owner-customizable QuickBooks chart-of-accounts mappings.
-- QuickBooks remains inactive and provider writes remain disabled. This migration
-- only strengthens Rally26's local mapping model and records owner review choices.

alter table quickbooks_account_mapping
    drop constraint quickbooks_mapping_type_check;

alter table quickbooks_account_mapping
    add constraint quickbooks_mapping_type_check check (mapping_type in (
        'PROGRAM_FEE_INCOME', 'SALES_INCOME', 'CONTRIBUTION_INCOME', 'SPONSORSHIP_INCOME',
        'REFUNDS', 'FEES_RECEIVABLE', 'BANK_CLEARING', 'PAYOUT_CLEARING'
    ));

alter table quickbooks_account_mapping
    add column external_account_fully_qualified_name text,
    add column external_account_sub_type text,
    add column compatibility_at_selection text not null default 'RECOMMENDED',
    add column warning_acknowledged boolean not null default false;

alter table quickbooks_account_mapping
    add constraint quickbooks_mapping_compatibility_check
        check (compatibility_at_selection in ('RECOMMENDED', 'ALLOWED_WITH_WARNING'));

comment on column quickbooks_account_mapping.external_account_id is
    'Stable QuickBooks Account.Id selected by the organization owner/admin; display names are snapshots only.';

comment on column quickbooks_account_mapping.compatibility_at_selection is
    'Compatibility classification when the mapping was explicitly saved. BLOCKED mappings are never persisted.';

comment on column quickbooks_account_mapping.warning_acknowledged is
    'True only when the owner/admin explicitly accepted a nonstandard but allowed account-type warning.';

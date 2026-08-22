package com.rally26.organization.domain

enum class ScopeMode { DIRECT, VIA_PARENT }

/**
 * One row of the org-owned table closure, computed this session by walking the live
 * `pg_constraint` FK graph from `organization` outward (675 FK constraints total in
 * this schema, 667 of them `NO ACTION` — this codebase deliberately never relies on DB
 * cascade, so this closure is what makes application-orchestrated cascade delete
 * possible at all). Excludes edges into `app_user` (never an organization-owned table)
 * and actor/creator columns (`*_by_user_id`, `reviewer`, etc. — those point outward to
 * accounts, not into the org's owned-data tree). Also excludes `audit_event` (all its
 * date partitions) and `platform_support_access` — founder decision this session:
 * audit/oversight records survive org closure, matching how the account-merge feature
 * already treats `audit_event` as permanent.
 *
 * [DIRECT] means the table has its own `organization_id` column — the vast majority.
 * [VIA_PARENT] means it's scoped one level of indirection through [parentTable], which
 * is itself always a [DIRECT] table in this list (confirmed for every entry below), so
 * `where {column} in (select id from {parentTable} where organization_id = :orgId)` is
 * always valid regardless of processing order — the parent hasn't been deleted yet,
 * since this list is already in dependency order (children before parents).
 *
 * Ordered via topological sort (leaves first) with one manual fix: the
 * `fundraising_game` <-> `fundraising_game_entry` cycle (a nullable "winner" back-
 * reference) is broken by [OrganizationDeletionLifecycleScanner] nulling
 * `winner_entry_id` before this list is processed, so ordering here ignores that edge.
 *
 * [financial] marks the 14 tables archived into `organization_financial_archive`
 * (full `to_jsonb` row snapshot) immediately before their own deletion — the rest are
 * deleted outright.
 *
 * Also excludes 7 messaging-safety tables (`message_entry`, `message_thread`,
 * `message_thread_member`, `message_recipient`, `message_contact_restriction`,
 * `message_moderation_event`, `message_safety_report`) — `reject_messaging_history_mutation()`
 * (V57/V58/V60) makes them append-only, and founder direction this session was to keep
 * that guarantee rather than bypass it for a hard delete: `message_entry`/
 * `message_recipient` are redacted in place instead (see
 * [OrganizationDeletionExecutorRepository.redactMessagingHistory]); the rest survive
 * untouched, the same as `audit_event`/`platform_support_access`.
 */
data class ScopedTable(
    val table: String,
    val mode: ScopeMode,
    val column: String,
    val parentTable: String?,
    val financial: Boolean,
)

val ORGANIZATION_DELETION_SCOPE: List<ScopedTable> =
    listOf(
        ScopedTable("announcement_recipient", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("athlete_storefront_product", ScopeMode.VIA_PARENT, "product_id", "product", false),
        ScopedTable("box_pool_box", ScopeMode.VIA_PARENT, "contribution_id", "contribution", false),
        ScopedTable("campaign_household_attribution_link", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("document_acknowledgment", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("eligibility_clearance", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("eligibility_evidence", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("event_rsvp", ScopeMode.VIA_PARENT, "participant_id", "participant", false),
        ScopedTable("event_source_connection", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("event_template", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("family_credit_application", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("family_credit_transfer", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_payment_installment_allocation", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("financial_correction", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("founding_org_promo_code", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fulfillment_history", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fulfillment_reprint", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fundraising_game_entry", ScopeMode.VIA_PARENT, "game_id", "fundraising_game", false),
        ScopedTable("google_calendar_connection_setting", ScopeMode.VIA_PARENT, "connection_id", "integration_connection", false),
        ScopedTable("google_calendar_event_mapping", ScopeMode.VIA_PARENT, "event_id", "event", false),
        ScopedTable("guardian_relationship", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("household_invitation", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("integration_connection_event", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("integration_connection_health_check", ScopeMode.VIA_PARENT, "connection_id", "integration_connection", false),
        ScopedTable("integration_oauth_state", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("integration_sync_issue", ScopeMode.VIA_PARENT, "sync_run_id", "integration_sync_run", false),
        ScopedTable("invitation", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("ledger_entry", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("media_variant", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("message_safe_sport_policy", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("offline_financial_record", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("onboarding_import_identity", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("order_item", ScopeMode.VIA_PARENT, "participant_id", "participant", true),
        ScopedTable("organization_credit_settings", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("organization_fundraising_settings", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("organization_markup_rule", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("organization_membership", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("organization_payout_account", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("organization_subscription", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("outbox_event", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("owner_onboarding", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("participant_team", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("payment_dispute", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("profile_correction_request", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("public_page", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("quickbooks_account_mapping", ScopeMode.VIA_PARENT, "connection_id", "integration_connection", false),
        ScopedTable("quickbooks_connection_setting", ScopeMode.VIA_PARENT, "connection_id", "integration_connection", false),
        ScopedTable("quickbooks_export_item", ScopeMode.VIA_PARENT, "batch_id", "quickbooks_export_batch", false),
        ScopedTable("reconciliation_issue", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("role_assignment", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("season_rollover_run", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("social_publishing_history", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("social_post_draft", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("sponsorship", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("sports_data_import_issue", ScopeMode.VIA_PARENT, "import_run_id", "sports_data_import_run", false),
        ScopedTable("sports_data_mapping", ScopeMode.VIA_PARENT, "connection_id", "integration_connection", false),
        ScopedTable("support_case", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("announcement", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("athlete_storefront", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("box_pool", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("contribution", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("eligibility_requirement", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("event", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("family_credit_grant", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_adjustment", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("fee_installment", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_payment", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("fulfillment", ScopeMode.VIA_PARENT, "order_id", "\"order\"", false),
        ScopedTable("fundraising_game", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("household_adult", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("media_assignment", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("product_variant", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("quickbooks_export_batch", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("reconciliation_run", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("sponsor", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("sponsorship_package", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("sports_data_import_run", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("\"order\"", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("campaign", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_payment_plan", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("integration_sync_run", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("product", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("tournament", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_assignment", ScopeMode.DIRECT, "organization_id", null, true),
        ScopedTable("integration_connection", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("manual_vendor", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("store", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("swag_brand_asset", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("fee_template", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("integration_credential_secret", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("media_asset", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("participant", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("team", ScopeMode.DIRECT, "organization_id", null, false),
        ScopedTable("household", ScopeMode.DIRECT, "organization_id", null, false),
    )

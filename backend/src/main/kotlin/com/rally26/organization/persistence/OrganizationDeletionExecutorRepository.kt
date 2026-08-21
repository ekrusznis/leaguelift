package com.rally26.organization.persistence

import com.rally26.organization.domain.ScopeMode
import com.rally26.organization.domain.ScopedTable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val MESSAGE_BODY_REDACTION_PLACEHOLDER = "[Message removed — organization closed]"
private const val MESSAGE_RECIPIENT_REDACTION_PLACEHOLDER = "[Organization Closed]"

/**
 * The actual cascade mechanics for [com.rally26.organization.application.OrganizationDeletionLifecycleScanner],
 * data-driven off [com.rally26.organization.domain.ORGANIZATION_DELETION_SCOPE] rather
 * than one hand-written method per table. Table/column names are interpolated
 * directly into SQL — safe here since every value comes from that compile-time list,
 * never from user input.
 */
@Repository
class OrganizationDeletionExecutorRepository(
    private val jdbcClient: JdbcClient,
) {
    private fun scopeWhereClause(table: ScopedTable): String =
        if (table.mode == ScopeMode.DIRECT) {
            "t.organization_id = :organizationId"
        } else {
            "t.${table.column} in (select id from ${table.parentTable} where organization_id = :organizationId)"
        }

    /** Full-row `to_jsonb` snapshot into `organization_financial_archive`, before that table's own deletion. */
    fun archiveFinancialTable(
        organizationId: UUID,
        table: ScopedTable,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                insert into organization_financial_archive (id, organization_id, source_table, source_id, snapshot_json, archived_at)
                select gen_random_uuid(), :organizationId, :tableName, t.id, to_jsonb(t), :now
                from ${table.table} t
                where ${scopeWhereClause(table)}
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("tableName", table.table)
            .param("now", Timestamp.from(now))
            .update()

    fun deleteScopedTable(
        organizationId: UUID,
        table: ScopedTable,
    ): Int =
        jdbcClient
            .sql("delete from ${table.table} t where ${scopeWhereClause(table)}")
            .param("organizationId", organizationId)
            .update()

    /** Breaks the one real cycle in the scope graph before the sweep begins — see [ScopedTable]'s doc comment. */
    fun breakFundraisingGameCycle(organizationId: UUID): Int =
        jdbcClient
            .sql("update fundraising_game set winner_entry_id = null where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .update()

    /**
     * `message_entry` rejects UPDATE (as well as DELETE) via
     * `reject_messaging_history_mutation()` (V57) to stop any product/API path from
     * tampering with messaging safety history. [redactMessagingHistory] below needs to
     * UPDATE it in place, so this lifts that guarantee for the current transaction only
     * (see V104's comment for why that's still safe for this one, terminal, Owner-
     * authorized operation). `set local` resets automatically at transaction end, so no
     * explicit reset call is needed.
     */
    fun enableMessagingHistoryRedaction(): Int = jdbcClient.sql("set local rally26.bypass_messaging_append_only = 'on'").update()

    /**
     * `message_entry`/`message_recipient` are excluded from [com.rally26.organization.domain.ORGANIZATION_DELETION_SCOPE]
     * (append-only, see [enableMessagingHistoryRedaction]) — redacted in place instead of
     * deleted, per founder direction this session. `message_thread`,
     * `message_thread_member`, `message_contact_restriction`, `message_moderation_event`,
     * and `message_safety_report` are left completely untouched, the same as
     * `audit_event`/`platform_support_access`.
     */
    fun redactMessagingHistory(organizationId: UUID): Int {
        val entryRows =
            jdbcClient
                .sql(
                    "update message_entry set body = :placeholder where organization_id = :organizationId and body <> :placeholder",
                ).param("organizationId", organizationId)
                .param("placeholder", MESSAGE_BODY_REDACTION_PLACEHOLDER)
                .update()
        val recipientRows =
            jdbcClient
                .sql(
                    """
                    update message_recipient
                    set display_name = :placeholder, email = null, phone = null
                    where organization_id = :organizationId and display_name <> :placeholder
                    """.trimIndent(),
                ).param("organizationId", organizationId)
                .param("placeholder", MESSAGE_RECIPIENT_REDACTION_PLACEHOLDER)
                .update()
        return entryRows + recipientRows
    }

    /** Tombstones the organization row itself — never deleted, blocks slug reuse, anchors the audit trail. */
    fun tombstoneOrganization(
        organizationId: UUID,
        now: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                update organization
                set status = 'ARCHIVED', contact_email = null, contact_phone = null,
                    address_line1 = null, address_line2 = null, address_city = null,
                    address_state = null, address_postal_code = null, address_country = null,
                    zelle_handle = null, updated_at = :now
                where id = :organizationId
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("now", Timestamp.from(now))
            .update()
}

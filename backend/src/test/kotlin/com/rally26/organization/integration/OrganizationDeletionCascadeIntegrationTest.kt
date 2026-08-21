package com.rally26.organization.integration

import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationDeletionLifecycleScanner
import com.rally26.organization.application.OrganizationDeletionService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.ORGANIZATION_DELETION_SCOPE
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.domain.ScopeMode
import com.rally26.organization.domain.ScopedTable
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [OrganizationDeletionLifecycleScanner.finalize] against a real Postgres
 * instance, iterating the full [ORGANIZATION_DELETION_SCOPE] (99 tables) rather than
 * spot-checking a handful — see the "Organization closure" section of the approved
 * plan for why this level of rigor was called for (application-orchestrated cascade
 * across a schema where 667/675 FK constraints are `NO ACTION`).
 *
 * Strategy: rather than hand-populating all 99 tables (impractical — many have
 * business-rule check constraints unrelated to this feature), a representative subset
 * spanning DIRECT/VIA_PARENT modes, financial archiving, and the one real cycle
 * (`fundraising_game` <-> `fundraising_game_entry`) is populated for two real
 * organizations. Every table in the scope list is then verified generically: DIRECT
 * tables via `organization_id`, VIA_PARENT tables via a pre-finalize snapshot of their
 * parent table's ids (captured before the parent rows themselves are deleted). This
 * proves the sweep is correct for the populated tables and, for the rest, that the
 * 99-statement transaction completes without an FK-ordering or SQL error and never
 * touches the other organization.
 */
class OrganizationDeletionCascadeIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired lateinit var organizationService: OrganizationService

    @Autowired lateinit var organizationDeletionService: OrganizationDeletionService

    @Autowired lateinit var organizationDeletionLifecycleScanner: OrganizationDeletionLifecycleScanner

    @Autowired lateinit var jdbcClient: JdbcClient

    private fun registerUser(prefix: String) =
        passwordAuthenticationService.toCurrentUser(
            passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", prefix),
        )

    private fun insertId(
        sql: String,
        params: Map<String, Any?>,
    ): UUID {
        var spec = jdbcClient.sql(sql)
        params.forEach { (k, v) -> spec = spec.param(k, v) }
        return spec.query(UUID::class.java).single()
    }

    private fun exec(
        sql: String,
        params: Map<String, Any?>,
    ) {
        var spec = jdbcClient.sql(sql)
        params.forEach { (k, v) -> spec = spec.param(k, v) }
        spec.update()
    }

    /** Populates a representative slice of the org-owned graph and returns key ids for later assertions. */
    private fun populate(
        organizationId: UUID,
        ownerId: UUID,
    ): Map<String, UUID> {
        val ids = mutableMapOf<String, UUID>()

        ids["team"] =
            insertId(
                "insert into team (id, organization_id, name, sport) values (gen_random_uuid(), :orgId, :name, 'SOCCER') returning id",
                mapOf("orgId" to organizationId, "name" to "Team ${UUID.randomUUID()}"),
            )
        ids["household"] =
            insertId(
                "insert into household (id, organization_id, display_name) values (gen_random_uuid(), :orgId, 'Test Household') returning id",
                mapOf("orgId" to organizationId),
            )
        ids["participant"] =
            insertId(
                """
                insert into participant (id, household_id, organization_id, first_name, last_name)
                values (gen_random_uuid(), :householdId, :orgId, 'Test', 'Athlete') returning id
                """.trimIndent(),
                mapOf("householdId" to ids.getValue("household"), "orgId" to organizationId),
            )
        exec(
            """
            insert into fee_assignment (id, organization_id, household_id, description, original_amount_minor)
            values (gen_random_uuid(), :orgId, :householdId, 'Registration fee', 10000)
            """.trimIndent(),
            mapOf("orgId" to organizationId, "householdId" to ids.getValue("household")),
        )
        ids["campaign"] =
            insertId(
                """
                insert into campaign (id, organization_id, name, slug, campaign_type, goal_amount_minor)
                values (gen_random_uuid(), :orgId, 'Test Campaign', :slug, 'ORGANIZATION_GENERAL', 100000) returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId, "slug" to "campaign-${UUID.randomUUID()}"),
            )
        exec(
            """
            insert into contribution (id, organization_id, campaign_id, amount_minor, currency)
            values (gen_random_uuid(), :orgId, :campaignId, 5000, 'USD')
            """.trimIndent(),
            mapOf("orgId" to organizationId, "campaignId" to ids.getValue("campaign")),
        )

        val fundraisingGameId =
            insertId(
                """
                insert into fundraising_game (id, organization_id, campaign_id, created_by_user_id, game_type, title)
                values (gen_random_uuid(), :orgId, :campaignId, :ownerId, 'FREE_PRIZE_DRAWING', 'Test Drawing') returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId, "campaignId" to ids.getValue("campaign"), "ownerId" to ownerId),
            )
        ids["fundraising_game"] = fundraisingGameId
        val fundraisingGameEntryId =
            insertId(
                """
                insert into fundraising_game_entry (id, game_id, display_name, email)
                values (gen_random_uuid(), :gameId, 'Test Entrant', :email) returning id
                """.trimIndent(),
                mapOf("gameId" to fundraisingGameId, "email" to "entrant-${UUID.randomUUID()}@example.com"),
            )
        // Deliberately creates the fundraising_game <-> fundraising_game_entry cycle this
        // feature must break before its delete sweep, or the entry's delete would violate
        // fundraising_game_winner_entry_fk.
        exec(
            "update fundraising_game set winner_entry_id = :entryId where id = :gameId",
            mapOf("entryId" to fundraisingGameEntryId, "gameId" to fundraisingGameId),
        )

        val storeId =
            insertId(
                "insert into store (id, organization_id, name, slug) values (gen_random_uuid(), :orgId, 'Test Store', :slug) returning id",
                mapOf("orgId" to organizationId, "slug" to "store-${UUID.randomUUID()}"),
            )
        val productId =
            insertId(
                """
                insert into product (id, organization_id, store_id, name, printify_blueprint_id)
                values (gen_random_uuid(), :orgId, :storeId, 'Test Tee', 1) returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId, "storeId" to storeId),
            )
        val productVariantId =
            insertId(
                """
                insert into product_variant
                    (id, organization_id, product_id, label, printify_print_provider_id, printify_variant_id, cost_minor, price_minor)
                values (gen_random_uuid(), :orgId, :productId, 'Medium', 1, 1, 500, 2000) returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId, "productId" to productId),
            )
        val orderId =
            insertId(
                "insert into \"order\" (id, organization_id, store_id) values (gen_random_uuid(), :orgId, :storeId) returning id",
                mapOf("orgId" to organizationId, "storeId" to storeId),
            )
        ids["order"] = orderId
        exec(
            """
            insert into order_item (id, order_id, product_variant_id, participant_id, quantity, unit_price_minor, unit_cost_minor)
            values (gen_random_uuid(), :orderId, :variantId, :participantId, 1, 2000, 500)
            """.trimIndent(),
            mapOf("orderId" to orderId, "variantId" to productVariantId, "participantId" to ids.getValue("participant")),
        )

        val sponsorId =
            insertId(
                "insert into sponsor (id, organization_id, name) values (gen_random_uuid(), :orgId, 'Test Sponsor') returning id",
                mapOf("orgId" to organizationId),
            )
        val sponsorshipPackageId =
            insertId(
                """
                insert into sponsorship_package (id, organization_id, name, price_minor)
                values (gen_random_uuid(), :orgId, 'Gold', 50000) returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId),
            )
        exec(
            """
            insert into sponsorship (id, organization_id, package_id, sponsor_id, amount_minor)
            values (gen_random_uuid(), :orgId, :packageId, :sponsorId, 50000)
            """.trimIndent(),
            mapOf("orgId" to organizationId, "packageId" to sponsorshipPackageId, "sponsorId" to sponsorId),
        )

        exec(
            """
            insert into event (id, organization_id, event_type, timezone, created_by_user_id, updated_by_user_id)
            values (gen_random_uuid(), :orgId, 'PRACTICE', 'America/New_York', :ownerId, :ownerId)
            """.trimIndent(),
            mapOf("orgId" to organizationId, "ownerId" to ownerId),
        )
        exec(
            """
            insert into support_case
                (id, idempotency_key, organization_id, requester_name, requester_email, category, subject, description)
            values (gen_random_uuid(), :key, :orgId, 'Test Requester', 'requester@example.com', 'OTHER', 'Subject', 'A description long enough to satisfy the check constraint.')
            """.trimIndent(),
            mapOf("key" to "support-${UUID.randomUUID()}", "orgId" to organizationId),
        )
        exec(
            """
            insert into announcement (id, organization_id, scope_type, scope_id, title, body, audience, created_by_user_id)
            values (gen_random_uuid(), :orgId, 'ORGANIZATION', :orgId, 'Test Announcement', 'This is a test announcement body.', 'ALL', :ownerId)
            """.trimIndent(),
            mapOf("orgId" to organizationId, "ownerId" to ownerId),
        )
        val messageThreadId =
            insertId(
                """
                insert into message_thread
                    (id, organization_id, scope_type, scope_id, idempotency_key, title, audience, created_by_user_id)
                values (gen_random_uuid(), :orgId, 'ORGANIZATION', :orgId, :key, 'Test Thread', 'ALL', :ownerId) returning id
                """.trimIndent(),
                mapOf("orgId" to organizationId, "key" to "thread-${UUID.randomUUID()}", "ownerId" to ownerId),
            )
        val messageEntryId =
            insertId(
                """
                insert into message_entry (id, organization_id, thread_id, sender_user_id, idempotency_key, body, sent_at)
                values (gen_random_uuid(), :orgId, :threadId, :ownerId, :key, 'Test message body.', now()) returning id
                """.trimIndent(),
                mapOf(
                    "orgId" to organizationId,
                    "threadId" to messageThreadId,
                    "ownerId" to ownerId,
                    "key" to "entry-${UUID.randomUUID()}",
                ),
            )
        exec(
            """
            insert into message_recipient (id, organization_id, message_id, recipient_key, recipient_type, display_name)
            values (gen_random_uuid(), :orgId, :messageId, :recipientKey, 'STAFF', 'Test Recipient')
            """.trimIndent(),
            mapOf("orgId" to organizationId, "messageId" to messageEntryId, "recipientKey" to "recipient-${UUID.randomUUID()}"),
        )
        ids["message_entry"] = messageEntryId

        return ids
    }

    private fun directCount(
        table: String,
        organizationId: UUID,
    ): Long =
        jdbcClient
            .sql("select count(*) from $table where organization_id = :orgId")
            .param("orgId", organizationId)
            .query(Long::class.java)
            .single()

    private fun parentIds(
        parentTable: String,
        organizationId: UUID,
    ): List<UUID> =
        jdbcClient
            .sql("select id from $parentTable where organization_id = :orgId")
            .param("orgId", organizationId)
            .query(UUID::class.java)
            .list()
            .filterNotNull()

    private fun viaParentCount(
        table: String,
        column: String,
        ids: List<UUID>,
    ): Long {
        if (ids.isEmpty()) return 0
        return jdbcClient
            .sql("select count(*) from $table where $column in (:ids)")
            .param("ids", ids)
            .query(Long::class.java)
            .single()
    }

    /** table -> (pre-finalize count, captured parent ids for VIA_PARENT tables). */
    private fun scopedSnapshot(organizationId: UUID): Map<ScopedTable, Pair<Long, List<UUID>>> {
        val parentIdCache = mutableMapOf<String, List<UUID>>()
        return ORGANIZATION_DELETION_SCOPE.associateWith { st ->
            if (st.mode == ScopeMode.DIRECT) {
                directCount(st.table, organizationId) to emptyList()
            } else {
                val ids = parentIdCache.getOrPut(st.parentTable!!) { parentIds(st.parentTable, organizationId) }
                viaParentCount(st.table, st.column, ids) to ids
            }
        }
    }

    private fun currentCount(
        st: ScopedTable,
        organizationId: UUID,
        capturedParentIds: List<UUID>,
    ): Long =
        if (st.mode == ScopeMode.DIRECT) directCount(st.table, organizationId) else viaParentCount(st.table, st.column, capturedParentIds)

    @Test
    fun `closing an org sweeps every scoped table, archives financials, breaks the cycle, and leaves other orgs untouched`() {
        val ownerA = registerUser("closeOwnerA")
        val ownerB = registerUser("closeOwnerB")
        val platformAdmin = registerUser("closePlatformAdmin")

        val orgA = organizationService.create("Cascade Org A", "cascade-a-${System.nanoTime()}", OrganizationType.TRAVEL_CLUB, ownerA)
        val orgB = organizationService.create("Cascade Org B", "cascade-b-${System.nanoTime()}", OrganizationType.TRAVEL_CLUB, ownerB)

        exec(
            "update organization set contact_email = 'owner@example.com', contact_phone = '555-1234' where id = :orgId",
            mapOf("orgId" to orgA.id),
        )

        val idsA = populate(orgA.id, ownerA.userId)
        val idsB = populate(orgB.id, ownerB.userId)

        exec(
            """
            insert into platform_support_access (id, platform_admin_user_id, organization_id, reason, expires_at)
            values (gen_random_uuid(), :adminId, :orgId, 'Investigating a billing discrepancy for QA', :expiresAt)
            """.trimIndent(),
            mapOf(
                "adminId" to platformAdmin.userId,
                "orgId" to orgA.id,
                "expiresAt" to Timestamp.from(Instant.now().plusSeconds(3600)),
            ),
        )

        // Sanity: the populated tables really do have rows before closure.
        assertTrue(directCount("team", orgA.id) > 0)
        assertTrue(directCount("contribution", orgA.id) > 0)
        assertTrue(directCount("fundraising_game", orgA.id) > 0)
        val auditCountBefore = directCount("audit_event", orgA.id)
        assertTrue(auditCountBefore > 0)
        val supportAccessCountBefore = directCount("platform_support_access", orgA.id)
        assertTrue(supportAccessCountBefore > 0)

        val preA = scopedSnapshot(orgA.id)
        val preB = scopedSnapshot(orgB.id)

        val request = organizationDeletionService.request(orgA.id, ownerA)
        exec(
            "update organization_deletion_request set scheduled_for = :past where id = :id",
            mapOf("past" to Timestamp.from(Instant.now().minusSeconds(1)), "id" to request.id),
        )

        organizationDeletionLifecycleScanner.scanAndFinalize()

        // Every scoped table is empty for the closed organization — the full 99-table sweep, not a spot-check.
        for (st in ORGANIZATION_DELETION_SCOPE) {
            val (_, capturedIds) = preA.getValue(st)
            assertEquals(0L, currentCount(st, orgA.id, capturedIds), "expected ${st.table} to be empty for the closed organization")
        }

        // The other organization is completely untouched.
        for (st in ORGANIZATION_DELETION_SCOPE) {
            val (preCount, capturedIds) = preB.getValue(st)
            assertEquals(
                preCount,
                currentCount(st, orgB.id, capturedIds),
                "expected ${st.table} for the untouched organization to be unaffected",
            )
        }

        // Financial rows were archived with a real snapshot before deletion.
        val archivedContribution =
            jdbcClient
                .sql(
                    "select snapshot_json ->> 'amount_minor' from organization_financial_archive where organization_id = :orgId and source_table = 'contribution'",
                ).param("orgId", orgA.id)
                .query(String::class.java)
                .single()
        assertEquals("5000", archivedContribution)
        val archivedOrder =
            jdbcClient
                .sql(
                    """
                    select count(*) from organization_financial_archive
                    where organization_id = :orgId and source_table = :table and source_id = :id
                    """.trimIndent(),
                ).param("orgId", orgA.id)
                .param("table", "\"order\"")
                .param("id", idsA.getValue("order"))
                .query(Long::class.java)
                .single()
        assertEquals(1L, archivedOrder)

        // Audit trail and platform-oversight records survive org closure (founder decision this
        // session) — closure itself adds more audit_event rows (request + completion), so this
        // asserts nothing was lost, not exact equality.
        assertTrue(directCount("audit_event", orgA.id) >= auditCountBefore)
        assertEquals(supportAccessCountBefore, directCount("platform_support_access", orgA.id))

        // Messaging history is append-only (reject_messaging_history_mutation, V57/V58/V60) —
        // closure redacts content in place rather than deleting the rows.
        val redactedBody =
            jdbcClient
                .sql("select body from message_entry where id = :id")
                .param("id", idsA.getValue("message_entry"))
                .query(String::class.java)
                .single()
        assertEquals("[Message removed — organization closed]", redactedBody)
        val (redactedName, redactedEmail) =
            jdbcClient
                .sql("select display_name, email from message_recipient where organization_id = :orgId")
                .param("orgId", orgA.id)
                .query { rs, _ -> rs.getString("display_name") to rs.getString("email") }
                .single()
        assertEquals("[Organization Closed]", redactedName)
        assertNull(redactedEmail)

        // The untouched organization's messaging history keeps its real content.
        val untouchedBody =
            jdbcClient
                .sql("select body from message_entry where id = :id")
                .param("id", idsB.getValue("message_entry"))
                .query(String::class.java)
                .single()
        assertEquals("Test message body.", untouchedBody)

        // The organization row survives as a tombstone, not deleted.
        val (status, contactEmail, contactPhone) =
            jdbcClient
                .sql("select status, contact_email, contact_phone from organization where id = :orgId")
                .param("orgId", orgA.id)
                .query { rs, _ -> Triple(rs.getString("status"), rs.getString("contact_email"), rs.getString("contact_phone")) }
                .single()
        assertEquals("ARCHIVED", status)
        assertNull(contactEmail)
        assertNull(contactPhone)

        // The owner's own personal login is untouched — only the org membership link is gone.
        val ownerStatus =
            jdbcClient
                .sql("select status from app_user where id = :id")
                .param("id", ownerA.userId)
                .query(String::class.java)
                .single()
        assertEquals("ACTIVE", ownerStatus)
    }

    @Test
    fun `canceling a pending closure request before the deadline leaves the organization fully intact`() {
        val owner = registerUser("cancelOwner")
        val organization = organizationService.create("Cancel Org", "cancel-org-${System.nanoTime()}", OrganizationType.TRAVEL_CLUB, owner)
        val ids = populate(organization.id, owner.userId)

        val request = organizationDeletionService.request(organization.id, owner)
        organizationDeletionService.cancel(organization.id, owner)

        organizationDeletionLifecycleScanner.scanAndFinalize()

        assertTrue(directCount("team", organization.id) > 0)
        assertTrue(directCount("fundraising_game", organization.id) > 0)
        assertEquals(1L, directCount("household", organization.id))
        assertTrue(ids.isNotEmpty())

        val status =
            jdbcClient
                .sql("select status from organization where id = :orgId")
                .param("orgId", organization.id)
                .query(String::class.java)
                .single()
        assertEquals("ACTIVE", status)

        val requestStatus =
            jdbcClient
                .sql("select status from organization_deletion_request where id = :id")
                .param("id", request.id)
                .query(String::class.java)
                .single()
        assertEquals("CANCELED", requestStatus)
    }
}

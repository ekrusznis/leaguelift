package com.rally26.platformadmin.persistence

import com.rally26.common.web.PageRequest
import com.rally26.platformadmin.domain.PlatformPaymentType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "every attempted payment" list is a hand-written `union all` across four
 * differently-shaped tables (order/contribution/sponsorship/fee_payment) — the real
 * risk here is a SQL-level bug (column-count/type mismatch across the union branches,
 * an invalid cast) that only a real Postgres execution can catch, not the Kotlin
 * compiler or a mocked-repository unit test. This runs the query for real; see
 * PlatformAdminConsoleServiceTest for the capability-gate/mapping unit test.
 *
 * [AbstractIntegrationTest] uses one Postgres container shared across the *entire* test
 * suite run (not per-class), so every assertion here filters by a freshly-generated,
 * never-reused organizationId rather than asserting the query returns globally empty —
 * other test classes running in the same suite genuinely do insert real
 * order/contribution/sponsorship/fee_payment rows into this same database.
 */
class PlatformAdminConsoleRepositoryPaymentsTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var repository: PlatformAdminConsoleRepository

    @Test
    fun `listPayments and countPayments execute the union query cleanly with no matching rows`() {
        val organizationId = UUID.randomUUID()
        val items = repository.listPayments(null, null, organizationId, null, null, null, null, PageRequest(0, 25))
        val total = repository.countPayments(null, null, organizationId, null, null, null, null)

        assertEquals(emptyList(), items)
        assertEquals(0L, total)
    }

    @Test
    fun `listPayments applies every filter without a SQL error`() {
        val items =
            repository.listPayments(
                type = "ORDER",
                status = "CONFIRMED",
                organizationId = UUID.randomUUID(),
                teamId = UUID.randomUUID(),
                query = "jane",
                dateFrom = Instant.parse("2026-01-01T00:00:00Z"),
                dateTo = Instant.parse("2026-12-31T00:00:00Z"),
                pageRequest = PageRequest(0, 25),
            )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `listPayments covers every payment type in its union branches`() {
        // No rows will match this never-reused organizationId, but this asserts the
        // query at least parses/executes for each type filter individually — a
        // column-count or cast mismatch in any one union branch would fail here too.
        val organizationId = UUID.randomUUID()
        PlatformPaymentType.entries.forEach { type ->
            val items = repository.listPayments(type.name, null, organizationId, null, null, null, null, PageRequest(0, 25))
            assertEquals(emptyList(), items)
        }
    }
}

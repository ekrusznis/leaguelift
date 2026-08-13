package com.rally26.platformadmin.persistence

import com.rally26.common.web.PageRequest
import com.rally26.eligibility.domain.ClearanceStatus
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The athlete roster query is a CTE with a correlated subquery (worst eligibility
 * status per participant) and a `group by` relying on primary-key functional
 * dependency (h.id/o.id) — real risks only a real Postgres execution can catch, not
 * the Kotlin compiler. Same "fresh never-reused organizationId, no seeded rows"
 * convention as PlatformAdminConsoleRepositoryPaymentsTest: this asserts the query
 * executes cleanly under every filter combination, not that filters narrow seeded
 * data (this repository class has no existing seed-data helper to build on).
 */
class PlatformAdminConsoleRepositoryRosterTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var repository: PlatformAdminConsoleRepository

    @Test
    fun `listAthletes and countAthletes execute cleanly with no matching rows`() {
        val organizationId = UUID.randomUUID()
        val items = repository.listAthletes(organizationId, null, null, null, null, PageRequest(0, 25))
        val total = repository.countAthletes(organizationId, null, null, null, null)

        assertEquals(emptyList(), items)
        assertEquals(0L, total)
    }

    @Test
    fun `listAthletes applies every filter without a SQL error`() {
        val items =
            repository.listAthletes(
                organizationId = UUID.randomUUID(),
                teamId = UUID.randomUUID(),
                householdId = UUID.randomUUID(),
                eligibilityStatus = ClearanceStatus.INELIGIBLE,
                query = "jane",
                pageRequest = PageRequest(0, 25),
            )

        assertEquals(emptyList(), items)
    }

    @Test
    fun `listAthletes filters cleanly by every eligibility status`() {
        val organizationId = UUID.randomUUID()
        ClearanceStatus.entries.forEach { status ->
            val items = repository.listAthletes(organizationId, null, null, status, null, PageRequest(0, 25))
            assertEquals(emptyList(), items)
        }
    }

    @Test
    fun `listCoaches and countCoaches execute cleanly with no matching rows`() {
        val organizationId = UUID.randomUUID()
        val items = repository.listCoaches(organizationId, null, null, PageRequest(0, 25))
        val total = repository.countCoaches(organizationId, null, null)

        assertEquals(emptyList(), items)
        assertEquals(0L, total)
    }

    @Test
    fun `listCoaches applies every filter without a SQL error`() {
        val items =
            repository.listCoaches(
                organizationId = UUID.randomUUID(),
                teamId = UUID.randomUUID(),
                query = "coach",
                pageRequest = PageRequest(0, 25),
            )

        assertEquals(emptyList(), items)
    }
}

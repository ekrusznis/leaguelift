package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.membership.application.MembershipService
import com.rally26.store.domain.MarkupType
import com.rally26.store.domain.OrganizationMarkupRule
import com.rally26.store.persistence.OrganizationMarkupRuleRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkupRuleServiceTest {
    private val markupRuleRepository = mockk<OrganizationMarkupRuleRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = MarkupRuleService(markupRuleRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val blueprintId = 5L

    private fun rule(
        printifyBlueprintId: Long?,
        markupType: MarkupType,
        markupValue: Int,
    ) = OrganizationMarkupRule(UUID.randomUUID(), orgId, printifyBlueprintId, markupType, markupValue, Instant.now(), Instant.now())

    @Test
    fun `computePrice uses the per-blueprint override when one exists`() {
        every { markupRuleRepository.findRule(orgId, blueprintId) } returns rule(blueprintId, MarkupType.PERCENTAGE, 5000)

        val price = service.computePrice(orgId, blueprintId, costMinor = 1000L)

        assertEquals(1500L, price) // 1000 + 50%
    }

    @Test
    fun `computePrice falls back to the org default when no per-blueprint override exists`() {
        every { markupRuleRepository.findRule(orgId, blueprintId) } returns null
        every { markupRuleRepository.findRule(orgId, null) } returns rule(null, MarkupType.PERCENTAGE, 2000)

        val price = service.computePrice(orgId, blueprintId, costMinor = 1000L)

        assertEquals(1200L, price) // 1000 + 20%
    }

    @Test
    fun `computePrice falls back to the system default when the org has configured nothing`() {
        every { markupRuleRepository.findRule(orgId, blueprintId) } returns null
        every { markupRuleRepository.findRule(orgId, null) } returns null

        val price = service.computePrice(orgId, blueprintId, costMinor = 1000L)

        assertEquals(1400L, price) // 1000 + system default 40%
    }

    @Test
    fun `computePrice supports a flat markup type`() {
        every { markupRuleRepository.findRule(orgId, blueprintId) } returns rule(blueprintId, MarkupType.FLAT, 500)

        val price = service.computePrice(orgId, blueprintId, costMinor = 1000L)

        assertEquals(1500L, price)
    }
}

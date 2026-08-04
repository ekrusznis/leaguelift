package com.rally26.actioncenter.application

import com.rally26.actioncenter.domain.ActionCenterPriority
import com.rally26.actioncenter.domain.ActionCenterType
import com.rally26.actioncenter.persistence.ActionCenterRepository
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.AuthorizationContext
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.ContextType
import com.rally26.common.web.CurrentUser
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionCenterServiceTest {
    private val repository = mockk<ActionCenterRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T16:00:00Z"), ZoneOffset.UTC)
    private val service = ActionCenterService(repository, authorizationService, clock)
    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `organization manager sees aggregated review work ordered by priority`() {
        every { authorizationService.listContexts(user) } returns listOf(
            AuthorizationContext(
                ContextType.ORGANIZATION, organizationId, organizationId, "North Jersey Volleyball", "OWNER",
                setOf(Capabilities.ORG_MANAGE),
            ),
        )
        every { repository.countPendingCorrections(organizationId) } returns 2
        every { repository.countOverdueFees(organizationId) } returns 3
        every { repository.countReviewableEvents(organizationId) } returns 1
        every { repository.countFulfillmentExceptions(organizationId) } returns 0
        every { repository.countPendingOfflineFinancialRecords(organizationId) } returns 0
        every { repository.countOverdueInstallments(organizationId) } returns 0
        every { repository.countLatestReconciliationIssues(organizationId) } returns 0
        every { repository.listGuardianFeeActions(user.userId, any()) } returns emptyList()
        every { repository.listGuardianDocumentActions(user.userId, any()) } returns emptyList()
        every { repository.listGuardianRsvpActions(user.userId, any()) } returns emptyList()
        every { repository.listAthleteRsvpActions(user.userId, any()) } returns emptyList()
        every { repository.listSupportCaseActions(user.userId, any()) } returns emptyList()

        val result = service.get(user)

        assertEquals(3, result.totalCount)
        assertEquals(2, result.highPriorityCount)
        assertTrue(result.items.take(2).all { it.priority == ActionCenterPriority.HIGH })
        assertTrue(result.items.any { it.type == ActionCenterType.EVENT_REVIEW })
    }
    @Test
    fun `organization manager sees pending offline financial verification work`() {
        every { authorizationService.listContexts(user) } returns listOf(
            AuthorizationContext(
                ContextType.ORGANIZATION, organizationId, organizationId, "North Jersey Volleyball", "OWNER",
                setOf(Capabilities.ORG_MANAGE),
            ),
        )
        every { repository.countPendingCorrections(organizationId) } returns 0
        every { repository.countOverdueFees(organizationId) } returns 0
        every { repository.countFulfillmentExceptions(organizationId) } returns 0
        every { repository.countPendingOfflineFinancialRecords(organizationId) } returns 2
        every { repository.countOverdueInstallments(organizationId) } returns 0
        every { repository.countLatestReconciliationIssues(organizationId) } returns 0
        every { repository.countReviewableEvents(organizationId) } returns 0
        every { repository.listGuardianFeeActions(user.userId, any()) } returns emptyList()
        every { repository.listGuardianDocumentActions(user.userId, any()) } returns emptyList()
        every { repository.listGuardianRsvpActions(user.userId, any()) } returns emptyList()
        every { repository.listAthleteRsvpActions(user.userId, any()) } returns emptyList()
        every { repository.listSupportCaseActions(user.userId, any()) } returns emptyList()

        val result = service.get(user)

        assertEquals(1, result.totalCount)
        assertEquals(ActionCenterType.OFFLINE_FINANCIAL_REVIEW, result.items.single().type)
        assertEquals(ActionCenterPriority.HIGH, result.items.single().priority)
        assertTrue(result.items.single().actionPath.endsWith("/financial-operations"))
    }

}

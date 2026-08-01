package com.leaguelift.actioncenter.application

import com.leaguelift.actioncenter.domain.ActionCenterPriority
import com.leaguelift.actioncenter.domain.ActionCenterType
import com.leaguelift.actioncenter.persistence.ActionCenterRepository
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.AuthorizationContext
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.authorization.domain.ContextType
import com.leaguelift.common.web.CurrentUser
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
}

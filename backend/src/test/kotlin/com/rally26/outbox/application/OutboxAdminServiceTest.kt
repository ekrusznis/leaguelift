package com.rally26.outbox.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.outbox.persistence.OutboxEventRepository
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OutboxAdminServiceTest {
    private val authorizationService = mockk<AuthorizationService>()
    private val outboxEventRepository = mockk<OutboxEventRepository>()
    private val service = OutboxAdminService(authorizationService, outboxEventRepository)

    private val currentUser = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)

    @Test
    fun `listDeadLetter requires the integration view capability`() {
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW) } throws
            ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> { service.listDeadLetter(currentUser) }
    }

    @Test
    fun `reprocess requires the integration manage capability, distinct from view`() {
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_MANAGE) } throws
            ForbiddenException("DENIED", "no")

        assertFailsWith<ForbiddenException> { service.reprocess(UUID.randomUUID(), currentUser) }
    }

    @Test
    fun `reprocess throws not found when no matching dead-letter or failed row exists`() {
        val id = UUID.randomUUID()
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_MANAGE) } returns Unit
        every { outboxEventRepository.reprocess(id) } returns false

        assertFailsWith<NotFoundException> { service.reprocess(id, currentUser) }
    }

    @Test
    fun `reprocess succeeds when a matching row was reset`() {
        val id = UUID.randomUUID()
        every { authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_MANAGE) } returns Unit
        every { outboxEventRepository.reprocess(id) } returns true

        service.reprocess(id, currentUser)
    }
}

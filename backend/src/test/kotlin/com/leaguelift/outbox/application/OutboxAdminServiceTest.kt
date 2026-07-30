package com.leaguelift.outbox.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.outbox.persistence.OutboxEventRepository
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

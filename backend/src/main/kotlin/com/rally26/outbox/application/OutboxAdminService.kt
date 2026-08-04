package com.rally26.outbox.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.persistence.OutboxEventRepository
import org.springframework.stereotype.Service

private const val ADMIN_LIST_LIMIT = 100

/**
 * Platform-admin operational visibility/recovery for the outbox worker (DESIGN-DOC.md
 * section 17's "provide admin visibility" / "allow controlled reprocessing"
 * requirements, Phase 8 slice 1, ADR-022). Distinct from
 * `PlatformAdminDashboardService.getOutboxHealth`'s bare counts — this exposes the
 * actual dead-letter/failed rows and a manual recovery action.
 */
@Service
class OutboxAdminService(
	private val authorizationService: AuthorizationService,
	private val outboxEventRepository: OutboxEventRepository,
) {

	fun listDeadLetter(currentUser: CurrentUser): List<OutboxEvent> {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
		return outboxEventRepository.findByStatus("DEAD_LETTER", ADMIN_LIST_LIMIT)
	}

	fun listFailed(currentUser: CurrentUser): List<OutboxEvent> {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_VIEW)
		return outboxEventRepository.findByStatus("FAILED", ADMIN_LIST_LIMIT)
	}

	fun reprocess(id: java.util.UUID, currentUser: CurrentUser) {
		authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_INTEGRATION_MANAGE)
		val reset = outboxEventRepository.reprocess(id)
		if (!reset) {
			throw NotFoundException(
				"OUTBOX_EVENT_NOT_REPROCESSABLE",
				"No DEAD_LETTER or FAILED outbox event with that id was found.",
			)
		}
	}
}

package com.rally26.outbox.application

import com.rally26.outbox.persistence.OutboxEventRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Writes an outbox_event row in the caller's existing transaction (the caller's
 * @Transactional application-service method), so the event is committed atomically
 * with the business state change it describes (DESIGN-DOC.md section 13.4).
 */
@Service
class OutboxWriter(private val outboxEventRepository: OutboxEventRepository) {

	fun write(
		aggregateType: String,
		aggregateId: UUID,
		organizationId: UUID?,
		eventType: String,
		payloadJson: String = "{}",
	) {
		outboxEventRepository.insert(aggregateType, aggregateId, organizationId, eventType, payloadJson)
	}
}

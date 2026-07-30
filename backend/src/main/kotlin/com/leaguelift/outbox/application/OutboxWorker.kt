package com.leaguelift.outbox.application

import com.leaguelift.config.OutboxWorkerProperties
import com.leaguelift.outbox.domain.OutboxEvent
import com.leaguelift.outbox.persistence.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

private val log = LoggerFactory.getLogger(OutboxWorker::class.java)

/**
 * The outbox consumer DESIGN-DOC.md section 17 has reserved since Phase 0 (Phase 8
 * slice 1, ADR-022). Runs on a fixed delay, claiming a batch of due `PENDING`/`FAILED`
 * rows (`OutboxEventRepository.claimBatch`, `for update skip locked`) and dispatching
 * each to the [OutboxEventHandler] registered for its `event_type`. An event type with
 * no registered handler is left claimed-then-untouched — it stays `PROCESSING` and
 * will need the platform-admin reprocess path once that's widened to cover stuck
 * `PROCESSING` rows too (see ADR-022 consequences); this is intentionally not silently
 * swallowed as a "no handler, mark done" no-op.
 */
@Component
class OutboxWorker(
	private val outboxEventRepository: OutboxEventRepository,
	handlers: List<OutboxEventHandler>,
	private val properties: OutboxWorkerProperties,
) {

	private val handlersByEventType: Map<String, OutboxEventHandler> = handlers.associateBy { it.eventType }

	init {
		log.info("Outbox worker registered handlers for event types: {}", handlersByEventType.keys)
	}

	@Scheduled(fixedDelayString = "\${leaguelift.outbox.worker.poll-interval-ms:5000}")
	fun pollAndDispatch() {
		if (!properties.enabled) return
		val claimed = outboxEventRepository.claimBatch(properties.batchSize)
		claimed.forEach(::dispatchOne)
	}

	/**
	 * Deliberately not `@Transactional` — a handler's own side effect (e.g. sending an
	 * email) isn't something a database rollback could undo anyway, and Spring's
	 * self-invocation caveat would silently drop the annotation here regardless (this
	 * method is called via a method reference from within the same bean, which bypasses
	 * the proxy). Each repository call below is already its own atomic statement.
	 */
	fun dispatchOne(event: OutboxEvent) {
		val handler = handlersByEventType[event.eventType]
		if (handler == null) {
			log.warn("No OutboxEventHandler registered for event_type '{}' (event {}) — leaving claimed, not processed.", event.eventType, event.id)
			return
		}
		try {
			handler.handle(event)
			outboxEventRepository.markProcessed(event.id)
		} catch (ex: Exception) {
			handleFailure(event, ex)
		}
	}

	private fun handleFailure(event: OutboxEvent, ex: Exception) {
		val message = ex.message ?: ex.javaClass.simpleName
		if (event.attemptCount >= properties.maxAttempts) {
			log.error("Outbox event {} ({}) exhausted {} attempts — moving to DEAD_LETTER.", event.id, event.eventType, event.attemptCount, ex)
			outboxEventRepository.markDeadLetter(event.id, message)
			return
		}
		val backoffSeconds = min(
			properties.backoffBaseSeconds * 2.0.pow(event.attemptCount - 1).toLong(),
			properties.backoffCapSeconds,
		)
		log.warn(
			"Outbox event {} ({}) failed on attempt {}/{} — retrying in {}s.",
			event.id, event.eventType, event.attemptCount, properties.maxAttempts, backoffSeconds, ex,
		)
		outboxEventRepository.markFailed(event.id, Instant.now().plusSeconds(backoffSeconds), message)
	}
}

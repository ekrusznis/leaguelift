package com.rally26.outbox.application

import com.rally26.outbox.domain.OutboxEvent

/**
 * Implemented once per outbox `event_type` this codebase actually knows how to act on
 * (Phase 8 slice 1, ADR-022). [OutboxWorker] collects every Spring bean implementing
 * this interface into an `eventType -> handler` map at startup — an event whose
 * `event_type` has no matching handler is left `PENDING` rather than silently dropped
 * (a forgotten handler is a bug worth noticing, not a no-op).
 *
 * [handle] should throw on any failure it wants retried (the worker handles backoff/
 * dead-lettering) rather than swallowing the error itself.
 */
interface OutboxEventHandler {
    val eventType: String

    fun handle(event: OutboxEvent)
}

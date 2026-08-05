package com.rally26.webhook.persistence

import com.rally26.webhook.domain.WebhookEvent
import com.rally26.webhook.domain.WebhookProcessingStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, provider, external_event_id, event_type, payload, payload_hash, signature_verified, processing_status, attempt_count, " +
        "related_entity_type, related_entity_id, received_at, processed_at, last_error"

@Repository
class WebhookEventRepository(
    private val jdbcClient: JdbcClient,
) {
    /** Null means this (provider, externalEventId) hasn't been recorded yet — safe to process. */
    fun findExisting(
        provider: String,
        externalEventId: String,
    ): WebhookEvent? =
        jdbcClient
            .sql("select $COLUMNS from webhook_event where provider = :provider and external_event_id = :externalEventId")
            .param("provider", provider)
            .param("externalEventId", externalEventId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        provider: String,
        externalEventId: String,
        eventType: String,
        payload: String,
        payloadHash: String,
        signatureVerified: Boolean,
        processingStatus: WebhookProcessingStatus,
        relatedEntityType: String?,
        relatedEntityId: UUID?,
        lastError: String?,
    ): WebhookEvent {
        val id = UUID.randomUUID()
        val receivedAt = Instant.now()
        val processedAt = if (processingStatus == WebhookProcessingStatus.FAILED) null else receivedAt
        jdbcClient
            .sql(
                """
                insert into webhook_event
                	(id, provider, external_event_id, event_type, payload, payload_hash, signature_verified, processing_status, attempt_count, related_entity_type, related_entity_id, received_at, processed_at, last_error)
                values
                	(:id, :provider, :externalEventId, :eventType, cast(:payload as jsonb), :payloadHash, :signatureVerified, :processingStatus, 1, :relatedEntityType, :relatedEntityId, :receivedAt, :processedAt, :lastError)
                """.trimIndent(),
            ).param("id", id)
            .param("provider", provider)
            .param("externalEventId", externalEventId)
            .param("eventType", eventType)
            .param("payload", payload)
            .param("payloadHash", payloadHash)
            .param("signatureVerified", signatureVerified)
            .param("processingStatus", processingStatus.name)
            .param("relatedEntityType", relatedEntityType)
            .param("relatedEntityId", relatedEntityId)
            .param("receivedAt", Timestamp.from(receivedAt))
            .param("processedAt", processedAt?.let { Timestamp.from(it) })
            .param("lastError", lastError)
            .update()
        return WebhookEvent(
            id,
            provider,
            externalEventId,
            eventType,
            payload,
            payloadHash,
            signatureVerified,
            processingStatus,
            1,
            relatedEntityType,
            relatedEntityId,
            receivedAt,
            processedAt,
            lastError,
        )
    }

    /** Platform-admin-only health aggregate (DESIGN-DOC.md section 10.2/18.2 webhook backlog/failures). */
    fun countByProcessingStatus(status: WebhookProcessingStatus): Long =
        jdbcClient
            .sql("select count(*) from webhook_event where processing_status = :status")
            .param("status", status.name)
            .query(Long::class.java)
            .single()

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): WebhookEvent =
        WebhookEvent(
            id = rs.getObject("id", UUID::class.java),
            provider = rs.getString("provider"),
            externalEventId = rs.getString("external_event_id"),
            eventType = rs.getString("event_type"),
            payload = rs.getString("payload"),
            payloadHash = rs.getString("payload_hash"),
            signatureVerified = rs.getBoolean("signature_verified"),
            processingStatus = WebhookProcessingStatus.valueOf(rs.getString("processing_status")),
            attemptCount = rs.getInt("attempt_count"),
            relatedEntityType = rs.getString("related_entity_type"),
            relatedEntityId = rs.getObject("related_entity_id", UUID::class.java),
            receivedAt = rs.getTimestamp("received_at").toInstant(),
            processedAt = rs.getTimestamp("processed_at")?.toInstant(),
            lastError = rs.getString("last_error"),
        )
}

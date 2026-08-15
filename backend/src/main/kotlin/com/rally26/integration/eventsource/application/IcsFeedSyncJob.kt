package com.rally26.integration.eventsource.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.IcsFeedSyncProperties
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.domain.PendingSourceEventSnapshot
import com.rally26.event.persistence.EventRepository
import com.rally26.integration.eventsource.domain.EventSourceConnection
import com.rally26.integration.eventsource.domain.EventSourceSyncStatus
import com.rally26.integration.eventsource.infra.IcsFeedFetcher
import com.rally26.integration.eventsource.persistence.EventSourceConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

private val log = LoggerFactory.getLogger(IcsFeedSyncJob::class.java)
private const val PROVIDER = "ICS_FEED"

/**
 * The ICS Feed connector's actual sync (Phase 12 slice 3, ADR-033) — slice 1 only
 * built connect/disconnect; nothing consumed a connection until this job. Mirrors
 * `FeePaymentReminderScanner`'s `@Scheduled` shape; one connection's failure (an
 * unreachable URL, a malformed feed) is caught and recorded per-connection, never
 * aborting the rest of the batch — the same posture `CsvEventImportService` already
 * established for row-level failures.
 */
@Component
class IcsFeedSyncJob(
    private val eventSourceConnectionRepository: EventSourceConnectionRepository,
    private val eventRepository: EventRepository,
    private val icsFeedFetcher: IcsFeedFetcher,
    private val properties: IcsFeedSyncProperties,
    private val objectMapper: ObjectMapper,
) {
    @Scheduled(cron = "\${rally26.ics-feed.sync.cron:0 */30 * * * *}")
    fun syncAll() {
        if (!properties.enabled) return
        val connections = eventSourceConnectionRepository.listActiveIcsFeedConnections()
        connections.forEach(::syncOne)
    }

    /**
     * Deliberately catches per-parsed-event, not around the whole method — an
     * exception escaping this `@Transactional` method would roll back every
     * successful upsert already made in this same sync, discarding real progress
     * over one bad event (the same reasoning `CsvEventImportService` already
     * applied to row-level failures).
     */
    @Transactional
    fun syncOne(connection: EventSourceConnection) {
        val feedUrl = connection.feedUrl
        if (feedUrl == null) {
            log.warn("ICS_FEED connection {} has no feed_url — skipping.", connection.id)
            return
        }
        val content =
            try {
                icsFeedFetcher.fetch(feedUrl)
            } catch (e: Exception) {
                recordFailure(connection, "Could not reach the feed: ${e.message ?: e.javaClass.simpleName}")
                return
            }
        val parsedEvents =
            try {
                IcsFeedParser.parse(content, ZoneId.of(connection.timezone))
            } catch (e: Exception) {
                recordFailure(connection, "Could not parse the feed: ${e.message ?: e.javaClass.simpleName}")
                return
            }

        var created = 0
        var touched = 0
        var failed = 0
        for (parsed in parsedEvents) {
            try {
                if (upsert(connection, parsed)) created++ else touched++
            } catch (e: Exception) {
                failed++
                log.warn("Failed to sync event {} from ICS_FEED connection {}: {}", parsed.uid, connection.id, e.message)
            }
        }

        if (failed == 0) {
            eventSourceConnectionRepository.recordSyncResult(connection.id, EventSourceSyncStatus.SUCCESS, null)
        } else {
            eventSourceConnectionRepository.recordSyncResult(
                connection.id,
                EventSourceSyncStatus.FAILED,
                "$failed of ${parsedEvents.size} events failed to sync",
            )
        }
        log.info(
            "Synced ICS_FEED connection {} ({}): {} created, {} touched, {} failed.",
            connection.id,
            connection.label,
            created,
            touched,
            failed,
        )
    }

    private fun recordFailure(
        connection: EventSourceConnection,
        message: String,
    ) {
        eventSourceConnectionRepository.recordSyncResult(connection.id, EventSourceSyncStatus.FAILED, message)
        log.warn("ICS_FEED connection {} ({}) failed to sync: {}", connection.id, connection.label, message)
    }

    /** Returns true if a new event was created, false if an existing one was matched (updated or left unchanged). */
    private fun upsert(
        connection: EventSourceConnection,
        parsed: ParsedIcsEvent,
    ): Boolean {
        val connectionId = connection.id.toString()
        val syncHash =
            sha256Hex(
                listOf(parsed.summary, parsed.description, parsed.location, parsed.startAt, parsed.endAt, parsed.status)
                    .joinToString("|") { it?.toString() ?: "" },
            )
        val now = Instant.now()
        val existing = eventRepository.findByExternalIdentity(connection.organizationId, PROVIDER, connectionId, parsed.uid)
        val status = mapStatus(parsed.status)

        if (existing == null) {
            eventRepository.insert(
                organizationId = connection.organizationId,
                teamId = connection.teamId,
                tournamentId = null,
                opponentTeamId = null,
                opponentName = null,
                eventType = EventType.OTHER,
                title = parsed.summary,
                description = parsed.description,
                startAt = parsed.startAt,
                endAt = parsed.endAt,
                arrivalAt = null,
                meetingAt = null,
                timezone = connection.timezone,
                venueName = parsed.location,
                address = null,
                latitude = null,
                longitude = null,
                area = null,
                meetingPoint = null,
                directionsNotes = null,
                visibility = if (connection.teamId != null) EventVisibility.TEAM else EventVisibility.ORGANIZATION,
                createdByUserId = connection.createdByUserId,
                sourceType = EventSourceType.ICS_FEED,
                provider = PROVIDER,
                connectionId = connectionId,
                externalEventId = parsed.uid,
                externalSyncHash = syncHash,
                sourceUpdatedAt = now,
                initialStatus = status ?: EventStatus.TENTATIVE,
            )
            return true
        }
        if (existing.externalSyncHash == syncHash) {
            // Already applied — nothing changed since the last time a human accepted this event's source data.
            return false
        }
        if (existing.pendingSourceHash == syncHash) {
            // Already staged, waiting on a human to review and apply it — don't re-stage the same change every poll.
            return false
        }
        val snapshot =
            PendingSourceEventSnapshot(
                title = parsed.summary,
                description = parsed.description,
                status = status,
                startAt = parsed.startAt?.toString(),
                endAt = parsed.endAt?.toString(),
                arrivalAt = null,
                venueName = parsed.location,
                address = null,
                area = null,
                opponentName = null,
            )
        eventRepository.stagePendingSourceUpdate(
            existing.id,
            connection.organizationId,
            objectMapper.writeValueAsString(snapshot),
            syncHash,
        )
        return false
    }

    /** RFC 5545's STATUS is optional and only ever CONFIRMED/TENTATIVE/CANCELLED for a VEVENT — anything else (or absent) is left for the caller's own default. */
    private fun mapStatus(rawStatus: String?): EventStatus? =
        when (rawStatus?.uppercase()) {
            "CONFIRMED" -> EventStatus.SCHEDULED
            "CANCELLED" -> EventStatus.CANCELLED
            "TENTATIVE" -> EventStatus.TENTATIVE
            else -> null
        }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

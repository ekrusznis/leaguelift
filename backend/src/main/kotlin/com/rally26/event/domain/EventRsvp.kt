package com.rally26.event.domain

import java.time.Instant
import java.util.UUID

enum class RsvpResponse { ATTENDING, NOT_ATTENDING, MAYBE, NO_RESPONSE }

/** Who submitted the response — a guardian or the athlete themself (self-link), or staff recording it on a family's behalf. */
enum class RsvpSource { SELF, GUARDIAN, ADMIN }

data class EventRsvp(
	val id: UUID,
	val eventId: UUID,
	val participantId: UUID,
	val response: RsvpResponse,
	val note: String?,
	val respondedByUserId: UUID,
	val source: RsvpSource,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class RsvpSummary(
	val attending: Long,
	val notAttending: Long,
	val maybe: Long,
	val noResponse: Long,
) {
	companion object {
		fun of(responses: List<EventRsvp>): RsvpSummary = RsvpSummary(
			attending = responses.count { it.response == RsvpResponse.ATTENDING }.toLong(),
			notAttending = responses.count { it.response == RsvpResponse.NOT_ATTENDING }.toLong(),
			maybe = responses.count { it.response == RsvpResponse.MAYBE }.toLong(),
			noResponse = responses.count { it.response == RsvpResponse.NO_RESPONSE }.toLong(),
		)
	}
}

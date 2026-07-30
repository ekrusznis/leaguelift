package com.leaguelift.event.application

import java.util.UUID

/** A single household's notification-eligible contact info — opt-out/opt-in already resolved to null-or-present by the writer, so the handler only ever needs a null check (mirrors `FeePaymentReminderHandler`'s convention). */
data class EventRecipientContact(val email: String?, val phone: String?)

/** Written by `EventService` for every meaningful, recipient-visible change (Phase 10 slice 4, ADR-029) — one outbox event per change type, so a single update touching both time and location fires two distinct, separately-worded notifications. */
data class EventChangeNotificationPayload(
	val eventId: UUID,
	val displayTitle: String,
	val changeSummary: String,
	val recipients: List<EventRecipientContact>,
)

/** Written by `EventRsvpService` on every RSVP change — recipients are team staff (email only; RSVP changes aren't SMS-worthy), not the household that just submitted its own response. */
data class RsvpChangeNotificationPayload(
	val eventId: UUID,
	val displayTitle: String,
	val participantName: String,
	val previousResponse: String?,
	val newResponse: String,
	val staffEmails: List<String>,
)

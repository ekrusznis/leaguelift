package com.rally26.integration.eventsource.infra

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Fetches an arbitrary external ICS feed URL (Phase 12 slice 3, ADR-033) — unlike
 * every other provider client in this codebase (Resend, Twilio, Printify), there is
 * no single fixed API base URL here: the target is whatever URL an org connected.
 * A plain, unauthenticated [RestClient] is enough — public ICS feeds need no
 * credential, matching `GoogleMapsDirectionsProvider`'s "genuinely keyless" posture.
 */
@Component
class IcsFeedFetcher {

	private val restClient = RestClient.create()

	fun fetch(feedUrl: String): String =
		restClient.get()
			.uri(feedUrl)
			.retrieve()
			.body(String::class.java)
			?: throw IllegalStateException("The feed returned an empty response.")
}

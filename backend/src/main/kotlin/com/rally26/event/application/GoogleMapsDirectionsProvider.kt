package com.rally26.event.application

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The only [MapsProvider] implementation (Phase 10 slice 3, ADR-028) — Google's
 * keyless directions-link format (`https://www.google.com/maps/dir/?api=1&destination=...`),
 * genuinely real and fully functional today, unlike every other provider in this
 * codebase's "logging-only until real credentials exist" stopgaps: this URL scheme
 * needs no API key, account, or credential at all — only the JS Maps SDK/Places/embed
 * APIs Google offers require one, and this feature doesn't use any of those. Prefers
 * coordinates (more precise, no geocoding ambiguity) over the address string when both
 * are present.
 */
@Component
class GoogleMapsDirectionsProvider : MapsProvider {

	override fun buildDirectionsUrl(address: String?, latitude: Double?, longitude: Double?): String? {
		val destination = when {
			latitude != null && longitude != null -> "$latitude,$longitude"
			!address.isNullOrBlank() -> address
			else -> return null
		}
		val encoded = URLEncoder.encode(destination, StandardCharsets.UTF_8)
		return "https://www.google.com/maps/dir/?api=1&destination=$encoded"
	}
}

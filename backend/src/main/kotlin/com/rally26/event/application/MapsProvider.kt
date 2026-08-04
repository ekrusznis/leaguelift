package com.rally26.event.application

/**
 * The `MapsProvider` seam DESIGN-DOC.md section 14.1A/17 reserves (Phase 10 slice 3,
 * ADR-028) — a provider-neutral directions-link builder from a validated address and
 * optional coordinates. Deliberately consumes only [address]/[latitude]/[longitude] —
 * never a private field like `Event.meetingPoint`/`directionsNotes` — so a generated
 * link can never leak private meeting-location notes into public-facing map metadata,
 * per section 14.1A's explicit rule.
 */
interface MapsProvider {
	/** Null when there's nothing to build a link from (no address, no coordinates). */
	fun buildDirectionsUrl(address: String?, latitude: Double?, longitude: Double?): String?
}

package com.rally26.event.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleMapsDirectionsProviderTest {

	private val provider = GoogleMapsDirectionsProvider()

	@Test
	fun `prefers coordinates over address when both are present`() {
		val url = provider.buildDirectionsUrl("123 Main St", 40.7128, -74.0060)

		assertEquals("https://www.google.com/maps/dir/?api=1&destination=40.7128%2C-74.006", url)
	}

	@Test
	fun `falls back to a URL-encoded address when there are no coordinates`() {
		val url = provider.buildDirectionsUrl("123 Main St, Springfield", null, null)

		assertTrue(url!!.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
		assertTrue(url.contains("Main+St"))
	}

	@Test
	fun `returns null with no address and no coordinates`() {
		assertNull(provider.buildDirectionsUrl(null, null, null))
	}

	@Test
	fun `returns null for a blank address`() {
		assertNull(provider.buildDirectionsUrl("   ", null, null))
	}
}

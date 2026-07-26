package com.leaguelift

import com.leaguelift.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test

/**
 * Smoke test: the full Spring context (security, Flyway migrations, JDBC) loads
 * cleanly against a real Postgres instance. Requires Docker — see
 * AbstractIntegrationTest.
 */
class LeagueLiftApiApplicationTests : AbstractIntegrationTest() {

	@Test
	fun `application context loads`() {
		// Intentionally empty: failure to load the context fails this test.
	}
}

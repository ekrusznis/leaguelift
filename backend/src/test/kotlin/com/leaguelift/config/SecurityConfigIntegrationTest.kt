package com.leaguelift.config

import com.leaguelift.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises DESIGN-DOC.md section 22.3 critical scenario "a test-profile authentication
 * bypass must never run in production" — and its corollary, an unauthenticated public
 * visitor being correctly blocked from a protected route (scenario 3) — through the real
 * HTTP layer and `SecurityConfig`'s actual filter chain, not a mocked/direct service call.
 * Every other "integration test" in this codebase autowires services directly against a
 * real Postgres instance, which never actually exercises `SecurityConfig` itself; this is
 * the first to go over real HTTP against the running application. Uses the plain JDK
 * `HttpClient` rather than `TestRestTemplate` — Spring Boot 4 no longer bundles
 * `TestRestTemplate` in `spring-boot-test` by default.
 */
class SecurityConfigIntegrationTest : AbstractIntegrationTest() {

	@LocalServerPort
	var port: Int = 0

	private val httpClient: HttpClient = HttpClient.newHttpClient()

	private fun get(path: String, bearerToken: String? = null): HttpResponse<String> {
		val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
		if (bearerToken != null) builder.header("Authorization", "Bearer $bearerToken")
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
	}

	@Test
	fun `an unauthenticated request to a protected endpoint is rejected with 401, not silently allowed`() {
		val response = get("/api/v1/me")

		assertEquals(401, response.statusCode())
	}

	@Test
	fun `an unauthenticated request to a public endpoint still succeeds`() {
		val response = get("/actuator/health")

		assertEquals(200, response.statusCode())
	}

	@Test
	fun `a real, freshly-issued JWT authenticates successfully against the real filter chain`() {
		val email = "security-config-test-${System.nanoTime()}@example.com"
		val registerRequest = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/auth/register"))
			.header("Content-Type", "application/json")
			.POST(
				HttpRequest.BodyPublishers.ofString(
					"""{"email":"$email","password":"password1234","firstName":"Security","lastName":"Test"}""",
				),
			)
			.build()
		val registerResponse = httpClient.send(registerRequest, HttpResponse.BodyHandlers.ofString())
		assertEquals(201, registerResponse.statusCode())
		val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(registerResponse.body())?.groupValues?.get(1)
		assertTrue(accessToken != null, "registration response should carry an accessToken: ${registerResponse.body()}")

		val meResponse = get("/api/v1/me", bearerToken = accessToken)

		assertEquals(200, meResponse.statusCode())
	}
}

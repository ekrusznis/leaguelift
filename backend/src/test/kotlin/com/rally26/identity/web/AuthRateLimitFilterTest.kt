package com.rally26.identity.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.common.error.ErrorResponse
import com.rally26.common.web.RequestIdProvider
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRateLimitFilterTest {

	private val objectMapper = ObjectMapper().findAndRegisterModules()
	private val filter = AuthRateLimitFilter(RequestIdProvider(), objectMapper)

	private fun loginRequestFrom(ip: String): MockHttpServletRequest {
		val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
		request.addHeader("X-Forwarded-For", ip)
		return request
	}

	@Test
	fun `requests to paths outside the rate-limited set are never blocked`() {
		val request = MockHttpServletRequest("GET", "/api/v1/auth/verify-email")
		repeat(50) {
			val response = MockHttpServletResponse()
			val chain = MockFilterChain()
			filter.doFilter(request, response, chain)
			assertTrue(chain.request != null, "request should have reached the rest of the chain")
		}
	}

	@Test
	fun `the first ten login attempts from one IP within the window succeed`() {
		repeat(10) { attempt ->
			val response = MockHttpServletResponse()
			val chain = MockFilterChain()
			filter.doFilter(loginRequestFrom("203.0.113.10"), response, chain)
			assertTrue(chain.request != null, "attempt ${attempt + 1} should have reached the rest of the chain")
			assertEquals(200, response.status)
		}
	}

	@Test
	fun `the eleventh login attempt from the same IP within the window is rejected with 429`() {
		repeat(10) {
			filter.doFilter(loginRequestFrom("203.0.113.20"), MockHttpServletResponse(), MockFilterChain())
		}

		val response = MockHttpServletResponse()
		val chain = MockFilterChain()
		filter.doFilter(loginRequestFrom("203.0.113.20"), response, chain)

		assertEquals(429, response.status)
		val body = objectMapper.readValue(response.contentAsString, ErrorResponse::class.java)
		assertEquals("RATE_LIMITED", body.code)
		assertEquals(null, chain.request, "the blocked request must never reach the real login handler")
	}

	@Test
	fun `two different client IPs are rate-limited independently`() {
		repeat(10) {
			filter.doFilter(loginRequestFrom("203.0.113.30"), MockHttpServletResponse(), MockFilterChain())
		}

		val response = MockHttpServletResponse()
		val chain = MockFilterChain()
		filter.doFilter(loginRequestFrom("203.0.113.31"), response, chain)

		assertTrue(chain.request != null, "a fresh IP must not be affected by another IP's attempt count")
		assertEquals(200, response.status)
	}
}

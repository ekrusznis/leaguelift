package com.rally26.common.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRateLimitFilterTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val filter = ApiRateLimitFilter(RequestIdProvider(), objectMapper)

    private fun requestFrom(
        ip: String,
        path: String,
        method: String = "GET",
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, path)
        request.addHeader("X-Forwarded-For", ip)
        return request
    }

    @Test
    fun `webhook and actuator paths are never rate limited`() {
        repeat(400) {
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(requestFrom("198.51.100.1", "/api/v1/webhooks/stripe", "POST"), response, chain)
            assertTrue(chain.request != null, "webhook traffic should never be throttled")
        }
        repeat(400) {
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(requestFrom("198.51.100.1", "/actuator/health"), response, chain)
            assertTrue(chain.request != null, "actuator traffic should never be throttled")
        }
    }

    @Test
    fun `an IP staying under the general limit is never blocked`() {
        repeat(300) { attempt ->
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(requestFrom("198.51.100.2", "/api/v1/organizations"), response, chain)
            assertTrue(chain.request != null, "request ${attempt + 1} of 300 should pass through")
            assertEquals(200, response.status)
        }
    }

    @Test
    fun `an IP that exceeds the general limit is rejected with 429`() {
        repeat(300) {
            filter.doFilter(requestFrom("198.51.100.3", "/api/v1/organizations"), MockHttpServletResponse(), MockFilterChain())
        }

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(requestFrom("198.51.100.3", "/api/v1/organizations"), response, chain)

        assertEquals(429, response.status)
        assertEquals(null, chain.request, "the blocked request must never reach the rest of the chain")
    }

    @Test
    fun `the general limit is shared across every endpoint for one IP, not tracked per path`() {
        repeat(300) { attempt ->
            val path = if (attempt % 2 == 0) "/api/v1/organizations" else "/api/v1/teams"
            filter.doFilter(requestFrom("198.51.100.4", path), MockHttpServletResponse(), MockFilterChain())
        }

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(requestFrom("198.51.100.4", "/api/v1/tournaments"), response, chain)

        assertEquals(429, response.status)
        assertEquals(null, chain.request, "301st request from the same IP should be blocked regardless of which endpoint it hits")
    }
}

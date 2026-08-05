package com.rally26.identity.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.common.web.RequestIdProvider
import com.rally26.common.web.SlidingWindowRateLimiter
import com.rally26.common.web.resolveClientIp
import com.rally26.common.web.writeRateLimitedResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Tight in-memory sliding-window rate limiter for the public, unauthenticated auth
 * endpoints (register/login/verify-resend/password-reset-request) — security-review
 * addition (2026-08). Before this filter existed, nothing stood between the internet
 * and `POST /api/v1/auth/login`: an attacker could script unlimited password guesses
 * against a known email, or hammer /register or /password-reset/request to spam a
 * target's inbox. Keyed by client IP + path so one abusive IP can't exhaust another
 * user's attempts.
 *
 * This is deliberately much stricter than [com.rally26.common.web.ApiRateLimitFilter]'s
 * general safety net, and runs in addition to it (both apply to these paths) — these
 * five endpoints are the ones an attacker can hit with zero credentials, so they get
 * the tighter bound.
 *
 * Ordered right after [com.rally26.common.web.RequestIdFilter] (same
 * [Ordered.HIGHEST_PRECEDENCE] tier, offset by one) so a blocked request still gets a
 * real request ID in its error body, and well before Spring Security's own filter
 * chain, so a flood of requests never even reaches authentication/authorization work.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AuthRateLimitFilter(
    private val requestIdProvider: RequestIdProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val limiter = SlidingWindowRateLimiter(windowSeconds = WINDOW_SECONDS, maxRequests = MAX_ATTEMPTS)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.requestURI !in RATE_LIMITED_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        val key = "${resolveClientIp(request)}:${request.requestURI}"
        if (!limiter.tryAcquire(key)) {
            writeRateLimitedResponse(
                response,
                objectMapper,
                requestIdProvider,
                "Too many attempts. Please wait a few minutes and try again.",
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private const val WINDOW_SECONDS = 15L * 60
        private const val MAX_ATTEMPTS = 10
        private val RATE_LIMITED_PATHS =
            setOf(
                "/api/v1/auth/register",
                "/api/v1/auth/register-owner",
                "/api/v1/auth/login",
                "/api/v1/auth/verify-email/resend",
                "/api/v1/auth/password-reset/request",
            )
    }
}

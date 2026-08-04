package com.rally26.common.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// * General-purpose, per-IP safety net across the whole API — security-review addition
// * (2026-08). Most endpoints already require a valid signed-in user, which is the real
// * access control (see [com.rally26.authorization.application.AuthorizationService]);
// * this exists on top of that as a much looser catch-all so a single IP can never hammer
// * the API without bound, whether or not the requests carry valid credentials — a leaked
// * or stolen JWT, a runaway script hitting a legitimate account, or plain unauthenticated
// * flooding all get the same backstop. Deliberately generous (not a brute-force guard —
// * see [com.rally26.identity.web.AuthRateLimitFilter] for that) so it never gets in the
// * way of normal dashboard usage.
// *
// * Scoped to `/api/**` except `/api/v1/webhooks/**`, which receives calls from Stripe/
// * Printify's own shared infrastructure rather than a single attributable client IP —
// * throttling that risks dropping a legitimate provider callback. `/actuator/**` is
// * excluded too; it's infrastructure monitoring (the deploy workflow's own health check),
// * not user-facing API surface.
// *
// * Ordered right after [AuthRateLimitFilter][com.rally26.identity.web.AuthRateLimitFilter]
// * (same [Ordered.HIGHEST_PRECEDENCE] tier), so the two rate limiters run back to back,
// * both still well before Spring Security's filter chain.

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class ApiRateLimitFilter(
    private val requestIdProvider: RequestIdProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val limiter = SlidingWindowRateLimiter(windowSeconds = WINDOW_SECONDS, maxRequests = MAX_REQUESTS)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI
        if (!path.startsWith("/api/") || path.startsWith(EXCLUDED_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        if (!limiter.tryAcquire(resolveClientIp(request))) {
            writeRateLimitedResponse(
                response,
                objectMapper,
                requestIdProvider,
                "Too many requests. Please slow down and try again shortly.",
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private const val WINDOW_SECONDS = 60L
        private const val MAX_REQUESTS = 300
        private const val EXCLUDED_PREFIX = "/api/v1/webhooks/"
    }
}

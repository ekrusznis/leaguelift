package com.rally26.common.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.common.error.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple in-memory, per-key sliding-window request counter — shared by
 * [com.rally26.identity.web.AuthRateLimitFilter] (tight limits on a handful of
 * sensitive, unauthenticated auth endpoints) and [ApiRateLimitFilter] (a much looser
 * catch-all safety net across every endpoint, since most of the API requires a valid
 * user already and the primary defense there is authentication itself — this exists so
 * a single IP, valid credentials or not, can never hammer the API without limit).
 * Security-review addition, 2026-08.
 *
 * Not distributed — resets on redeploy and isn't shared across instances if the backend
 * is ever horizontally scaled beyond the single droplet ADR-008 describes. Acceptable at
 * this stage; revisit (e.g. move to Redis) if that changes.
 */
class SlidingWindowRateLimiter(
    private val windowSeconds: Long,
    private val maxRequests: Int,
) {
    private class Window(
        @Volatile var windowStart: Instant,
        @Volatile var count: Int,
    )

    private val hits = ConcurrentHashMap<String, Window>()

    /** Records one request against [key] and returns true if [key] is still within the allowed rate. */
    fun tryAcquire(key: String): Boolean {
        val now = Instant.now()
        val count =
            hits
                .compute(key) { _, existing ->
                    if (existing == null || existing.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                        Window(now, 1)
                    } else {
                        existing.count += 1
                        existing
                    }
                }!!
                .count
        return count <= maxRequests
    }
}

/**
 * The real client IP, preferring what Caddy forwards over the raw socket address. This
 * service only ever sits behind Caddy's `reverse_proxy` (see infra/digitalocean/Caddyfile),
 * which sets `X-Forwarded-For` — without this, every request would appear to come from
 * Caddy's own container IP on the compose network, making IP-keyed rate limiting useless.
 */
fun resolveClientIp(request: HttpServletRequest): String =
    request
        .getHeader("X-Forwarded-For")
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.remoteAddr

/** Writes the standard 429 error envelope, matching [com.rally26.common.error.GlobalExceptionHandler]'s shape even though this runs in a filter, outside that advice's reach. */
fun writeRateLimitedResponse(
    response: HttpServletResponse,
    objectMapper: ObjectMapper,
    requestIdProvider: RequestIdProvider,
    message: String,
) {
    response.status = HttpStatus.TOO_MANY_REQUESTS.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = "UTF-8"
    val body =
        ErrorResponse(
            code = "RATE_LIMITED",
            message = message,
            requestId = requestIdProvider.currentRequestId(),
        )
    response.writer.write(objectMapper.writeValueAsString(body))
}

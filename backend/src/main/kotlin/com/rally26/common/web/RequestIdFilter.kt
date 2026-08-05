package com.rally26.common.web

import com.rally26.config.RequestIdProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

const val REQUEST_ID_MDC_KEY = "requestId"

/**
 * Assigns (or propagates) a request ID for every incoming request, exposes it on the
 * response header, and puts it in the logging MDC so structured logs can be
 * correlated with client-visible error responses (DESIGN-DOC.md sections 13.3, 23.2).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter(
    private val requestIdProperties: RequestIdProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val headerName = requestIdProperties.headerName
        val incoming = request.getHeader(headerName)?.takeIf { it.isNotBlank() }
        val requestId = incoming ?: "req_${UUID.randomUUID()}"
        response.setHeader(headerName, requestId)
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }
}

package com.rally26.common.web

import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Reads the request ID set by [RequestIdFilter] out of the logging MDC so it can be
 * included in error response bodies without threading a request/response object
 * through every layer.
 */
@Component
class RequestIdProvider {
    fun currentRequestId(): String = MDC.get(REQUEST_ID_MDC_KEY) ?: "req_unknown"
}

package com.rally26.common.error

import com.rally26.common.web.RequestIdProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates exceptions into the standard error envelope (DESIGN-DOC.md section
 * 13.3). Never leaks stack traces, SQL, or internal exception class names to clients.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val requestIdProvider: RequestIdProvider,
) {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {
        val body =
            ErrorResponse(
                code = ex.code,
                message = ex.message,
                requestId = requestIdProvider.currentRequestId(),
                fieldErrors = ex.fieldErrors,
            )
        return ResponseEntity.status(ex.status).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors =
            ex.bindingResult.fieldErrors.map {
                FieldError(field = it.field, message = it.defaultMessage ?: "Invalid value")
            }
        val body =
            ErrorResponse(
                code = "VALIDATION_FAILED",
                message = "One or more fields are invalid.",
                requestId = requestIdProvider.currentRequestId(),
                fieldErrors = fieldErrors,
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        // A request body Jackson cannot bind (missing required field, wrong type,
        // malformed JSON) is a client-input problem, not a server error. As with
        // handleUnexpected, the real cause is logged server-side keyed by requestId only —
        // never surfaced in the response body, which could leak internal exception/class
        // details.
        val requestId = requestIdProvider.currentRequestId()
        log.warn("Malformed request body for requestId={}", requestId, ex)
        val body =
            ErrorResponse(
                code = "MALFORMED_REQUEST_BODY",
                message = "The request body could not be read. Check for missing or invalid fields.",
                requestId = requestId,
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        // A missing required query/path param is a client-input problem, same treatment
        // as handleMalformedBody -- found via a guardian-portal fix (2026-08-05) where
        // this fell through to handleUnexpected's opaque 500 instead.
        val body =
            ErrorResponse(
                code = "MISSING_REQUEST_PARAMETER",
                message = "Required parameter '${ex.parameterName}' is missing.",
                requestId = requestIdProvider.currentRequestId(),
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
        // Same class of bug as handleMissingParameter, same fix: a missing required
        // header (e.g. Stripe-Signature on a malformed/probing webhook POST) is a
        // client-input problem, not a server error — without this handler it fell
        // through to handleUnexpected's opaque 500 and logged a false-positive
        // ERROR-level "unhandled exception" for routine malformed requests.
        val body =
            ErrorResponse(
                code = "MISSING_REQUEST_HEADER",
                message = "Required header '${ex.headerName}' is missing.",
                requestId = requestIdProvider.currentRequestId(),
            )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        // Deliberately does not include ex.message or ex::class.simpleName in the
        // response body: those can leak internal details. Full detail goes to logs
        // only, keyed by requestId so it can be correlated with the client-visible
        // error.
        val requestId = requestIdProvider.currentRequestId()
        log.error("Unhandled exception for requestId={}", requestId, ex)
        val body =
            ErrorResponse(
                code = "INTERNAL_ERROR",
                message = "An unexpected error occurred.",
                requestId = requestId,
            )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}

package com.leaguelift.common.error

import com.leaguelift.common.web.RequestIdProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
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
		val body = ErrorResponse(
			code = ex.code,
			message = ex.message,
			requestId = requestIdProvider.currentRequestId(),
			fieldErrors = ex.fieldErrors,
		)
		return ResponseEntity.status(ex.status).body(body)
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
		val fieldErrors = ex.bindingResult.fieldErrors.map {
			FieldError(field = it.field, message = it.defaultMessage ?: "Invalid value")
		}
		val body = ErrorResponse(
			code = "VALIDATION_FAILED",
			message = "One or more fields are invalid.",
			requestId = requestIdProvider.currentRequestId(),
			fieldErrors = fieldErrors,
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
		val body = ErrorResponse(
			code = "INTERNAL_ERROR",
			message = "An unexpected error occurred.",
			requestId = requestId,
		)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
	}
}

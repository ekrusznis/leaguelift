package com.leaguelift.common.error

/**
 * Standard API error envelope. See DESIGN-DOC.md section 13.3.
 * Never include stack traces, SQL, secrets, provider payloads, or internal exception
 * names in [message] or [fieldErrors].
 */
data class ErrorResponse(
	val code: String,
	val message: String,
	val requestId: String,
	val fieldErrors: List<FieldError> = emptyList(),
)

data class FieldError(
	val field: String,
	val message: String,
)

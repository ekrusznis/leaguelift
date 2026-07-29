package com.leaguelift.common.error

import org.springframework.http.HttpStatus

/**
 * Base type for domain/application errors that should be translated into a specific
 * HTTP status and a stable machine-readable [code]. Application services throw
 * subclasses of this rather than generic exceptions so [GlobalExceptionHandler] can
 * produce a consistent envelope without inspecting exception messages.
 */
sealed class ApiException(
	val code: String,
	override val message: String,
	val status: HttpStatus,
	val fieldErrors: List<FieldError> = emptyList(),
) : RuntimeException(message)

class NotFoundException(code: String, message: String) :
	ApiException(code, message, HttpStatus.NOT_FOUND)

class ConflictException(code: String, message: String) :
	ApiException(code, message, HttpStatus.CONFLICT)

class ForbiddenException(code: String, message: String) :
	ApiException(code, message, HttpStatus.FORBIDDEN)

class UnauthorizedException(code: String, message: String) :
	ApiException(code, message, HttpStatus.UNAUTHORIZED)

/** An external provider (Stripe, DigitalOcean Spaces, etc.) rejected the request or is unreachable — not the caller's fault. */
class ServiceUnavailableException(code: String, message: String) :
	ApiException(code, message, HttpStatus.SERVICE_UNAVAILABLE)

class ValidationException(
	message: String,
	fieldErrors: List<FieldError> = emptyList(),
) : ApiException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST, fieldErrors)

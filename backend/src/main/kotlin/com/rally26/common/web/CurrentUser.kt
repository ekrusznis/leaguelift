package com.rally26.common.web

import java.util.UUID

/**
 * The authenticated caller, resolved from the validated internal JWT (or the
 * local-dev bypass principal — see docs/security.md) and mapped to an internal
 * app_user. Application services depend on this, never on the raw JWT/Authentication
 * object, so authorization logic stays independent of the token/session mechanism.
 */
data class CurrentUser(
	val userId: UUID,
	val email: String,
	val displayName: String,
	val platformAdministrator: Boolean = false,
)

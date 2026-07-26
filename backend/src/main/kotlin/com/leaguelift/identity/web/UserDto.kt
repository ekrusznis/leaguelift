package com.leaguelift.identity.web

import com.leaguelift.identity.domain.AppUser
import java.util.UUID

data class UserResponse(
	val id: UUID,
	val email: String,
	val displayName: String,
	val status: String,
)

fun AppUser.toResponse() = UserResponse(
	id = id,
	email = email,
	displayName = displayName,
	status = status.name,
)

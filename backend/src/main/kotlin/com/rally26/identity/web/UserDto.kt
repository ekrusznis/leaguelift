package com.rally26.identity.web

import com.rally26.identity.domain.AppUser
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: String,
)

fun AppUser.toResponse() =
    UserResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = status.name,
    )

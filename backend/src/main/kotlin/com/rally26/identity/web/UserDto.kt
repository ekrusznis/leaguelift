package com.rally26.identity.web

import com.rally26.identity.application.ResolvedAvatar
import com.rally26.identity.domain.AppUser
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: String,
    val avatarUrl: String?,
    val avatarSeed: String,
    val avatarStyle: String,
)

fun AppUser.toResponse(avatar: ResolvedAvatar) =
    UserResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = status.name,
        avatarUrl = avatar.avatarUrl,
        avatarSeed = avatar.avatarSeed,
        avatarStyle = avatar.avatarStyle,
    )

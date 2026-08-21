package com.rally26.identity.domain

import java.time.Instant
import java.util.UUID

enum class AppUserStatus { ACTIVE, SUSPENDED, PENDING_EMAIL_VERIFICATION }

/** A linked external sign-in identity (Phase 37) — see V78's migration comment for why this coexists with password auth rather than replacing it. */
enum class OAuthProvider { GOOGLE, APPLE }

data class AppUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: AppUserStatus,
    val passwordHash: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val provider: OAuthProvider? = null,
    val providerSubject: String? = null,
    val avatarObjectKey: String? = null,
    val avatarSeed: String? = null,
    val avatarStyle: String? = null,
)

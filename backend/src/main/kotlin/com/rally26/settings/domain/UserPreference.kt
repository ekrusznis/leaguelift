package com.rally26.settings.domain

import java.time.Instant
import java.util.UUID

enum class AppearancePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreference(
    val userId: UUID,
    val appearance: AppearancePreference,
    val createdAt: Instant,
    val updatedAt: Instant,
)

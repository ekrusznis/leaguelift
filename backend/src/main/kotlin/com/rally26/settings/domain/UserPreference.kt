package com.rally26.settings.domain

import java.time.Instant
import java.util.UUID

enum class AppearancePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Whether a guardian's newly uploaded household media (`HouseholdMediaService.assign`) starts Public or Household-only. Never affects an existing item — only the default applied at upload time. */
enum class MediaVisibilityDefault {
    PRIVATE,
    PUBLIC,
}

data class UserPreference(
    val userId: UUID,
    val appearance: AppearancePreference,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Appended with a default so older positional test fixtures remain source-compatible.
    val defaultMediaVisibility: MediaVisibilityDefault = MediaVisibilityDefault.PRIVATE,
)

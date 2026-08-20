package com.rally26.settings.application

import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.domain.MediaVisibilityDefault
import com.rally26.settings.persistence.UserPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PersonalPreferences(
    val appearance: AppearancePreference,
    // Appended with a default so older positional test fixtures remain source-compatible.
    val defaultMediaVisibility: MediaVisibilityDefault = MediaVisibilityDefault.PRIVATE,
)

@Service
class UserPreferenceService(
    private val repository: UserPreferenceRepository,
) {
    fun get(userId: UUID): PersonalPreferences {
        val stored = repository.findByUserId(userId)
        return PersonalPreferences(
            appearance = stored?.appearance ?: AppearancePreference.SYSTEM,
            defaultMediaVisibility = stored?.defaultMediaVisibility ?: MediaVisibilityDefault.PRIVATE,
        )
    }

    /** Used directly by `HouseholdMediaService.assign` to pick the visibility a newly uploaded item starts at — never touches an existing item. */
    fun defaultMediaVisibility(userId: UUID): MediaVisibilityDefault = get(userId).defaultMediaVisibility

    @Transactional
    fun updateAppearance(
        userId: UUID,
        appearance: AppearancePreference,
    ): PersonalPreferences {
        val updated = repository.upsertAppearance(userId, appearance)
        return PersonalPreferences(appearance = updated.appearance, defaultMediaVisibility = updated.defaultMediaVisibility)
    }

    @Transactional
    fun updateDefaultMediaVisibility(
        userId: UUID,
        defaultMediaVisibility: MediaVisibilityDefault,
    ): PersonalPreferences {
        val updated = repository.upsertDefaultMediaVisibility(userId, defaultMediaVisibility)
        return PersonalPreferences(appearance = updated.appearance, defaultMediaVisibility = updated.defaultMediaVisibility)
    }
}

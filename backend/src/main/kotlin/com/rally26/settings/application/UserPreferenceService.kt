package com.rally26.settings.application

import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.persistence.UserPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PersonalPreferences(
    val appearance: AppearancePreference,
)

@Service
class UserPreferenceService(
    private val repository: UserPreferenceRepository,
) {
    fun get(userId: UUID): PersonalPreferences =
        PersonalPreferences(
            appearance = repository.findByUserId(userId)?.appearance ?: AppearancePreference.SYSTEM,
        )

    @Transactional
    fun updateAppearance(
        userId: UUID,
        appearance: AppearancePreference,
    ): PersonalPreferences =
        PersonalPreferences(
            appearance = repository.upsertAppearance(userId, appearance).appearance,
        )
}

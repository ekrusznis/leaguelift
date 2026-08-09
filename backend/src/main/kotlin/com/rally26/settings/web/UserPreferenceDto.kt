package com.rally26.settings.web

import com.rally26.settings.application.PersonalPreferences
import com.rally26.settings.domain.AppearancePreference

data class UserPreferenceResponse(
    val appearance: AppearancePreference,
)

data class UpdateUserPreferenceRequest(
    val appearance: AppearancePreference,
)

fun PersonalPreferences.toResponse() =
    UserPreferenceResponse(
        appearance = appearance,
    )

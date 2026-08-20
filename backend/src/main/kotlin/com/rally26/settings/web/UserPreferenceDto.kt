package com.rally26.settings.web

import com.rally26.settings.application.PersonalPreferences
import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.domain.MediaVisibilityDefault

data class UserPreferenceResponse(
    val appearance: AppearancePreference,
    val defaultMediaVisibility: MediaVisibilityDefault,
)

data class UpdateUserPreferenceRequest(
    val appearance: AppearancePreference,
)

data class UpdateDefaultMediaVisibilityRequest(
    val defaultMediaVisibility: MediaVisibilityDefault,
)

fun PersonalPreferences.toResponse() =
    UserPreferenceResponse(
        appearance = appearance,
        defaultMediaVisibility = defaultMediaVisibility,
    )

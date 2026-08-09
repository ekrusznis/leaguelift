package com.rally26.settings.web

import com.rally26.common.web.CurrentUser
import com.rally26.settings.application.NotificationPreferenceService
import com.rally26.settings.application.NotificationPreferences
import com.rally26.settings.application.PersonalPreferences
import com.rally26.settings.application.UserPreferenceService
import com.rally26.settings.domain.AppearancePreference
import com.rally26.settings.domain.NotificationPreferenceState
import com.rally26.settings.domain.NotificationTopic
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test

class SettingsControllerSelfScopeTest {
    private val appearanceService = mockk<UserPreferenceService>()
    private val notificationService = mockk<NotificationPreferenceService>()
    private val appearanceController = UserPreferenceController(appearanceService)
    private val notificationController = NotificationPreferenceController(notificationService)

    @Test
    fun `appearance endpoints use only the authenticated user id`() {
        val currentUser = currentUser()
        val otherUserId = UUID.randomUUID()

        every { appearanceService.get(currentUser.userId) } returns
            PersonalPreferences(AppearancePreference.SYSTEM)
        every {
            appearanceService.updateAppearance(currentUser.userId, AppearancePreference.DARK)
        } returns PersonalPreferences(AppearancePreference.DARK)

        appearanceController.get(currentUser)
        appearanceController.update(
            UpdateUserPreferenceRequest(AppearancePreference.DARK),
            currentUser,
        )

        verify(exactly = 1) { appearanceService.get(currentUser.userId) }
        verify(exactly = 1) {
            appearanceService.updateAppearance(currentUser.userId, AppearancePreference.DARK)
        }
        verify(exactly = 0) { appearanceService.get(otherUserId) }
        verify(exactly = 0) {
            appearanceService.updateAppearance(otherUserId, any())
        }
    }

    @Test
    fun `notification endpoints use only the authenticated user id`() {
        val currentUser = currentUser()
        val otherUserId = UUID.randomUUID()
        val response = NotificationPreferences(smsConsent = false, topics = emptyList())

        every { notificationService.get(currentUser.userId) } returns response
        every {
            notificationService.updateTopic(
                currentUser.userId,
                NotificationTopic.MESSAGES,
                NotificationPreferenceState.ENABLED,
                NotificationPreferenceState.DISABLED,
                NotificationPreferenceState.DEFAULT,
            )
        } returns response
        every { notificationService.setSmsConsent(currentUser.userId, true) } returns response

        notificationController.get(currentUser)
        notificationController.updateTopic(
            NotificationTopic.MESSAGES,
            UpdateNotificationTopicRequest(
                inApp = NotificationPreferenceState.ENABLED,
                email = NotificationPreferenceState.DISABLED,
                sms = NotificationPreferenceState.DEFAULT,
            ),
            currentUser,
        )
        notificationController.updateSmsConsent(UpdateSmsConsentRequest(consented = true), currentUser)

        verify(exactly = 1) { notificationService.get(currentUser.userId) }
        verify(exactly = 1) {
            notificationService.updateTopic(
                currentUser.userId,
                NotificationTopic.MESSAGES,
                NotificationPreferenceState.ENABLED,
                NotificationPreferenceState.DISABLED,
                NotificationPreferenceState.DEFAULT,
            )
        }
        verify(exactly = 1) { notificationService.setSmsConsent(currentUser.userId, true) }
        verify(exactly = 0) { notificationService.get(otherUserId) }
        verify(exactly = 0) {
            notificationService.setSmsConsent(otherUserId, any())
        }
    }

    private fun currentUser() =
        CurrentUser(
            userId = UUID.randomUUID(),
            email = "test@user.com",
            displayName = "Test User",
        )
}

package com.rally26.settings.web

import com.rally26.common.web.CurrentUser
import com.rally26.settings.application.NotificationPreferenceService
import com.rally26.settings.domain.NotificationTopic
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class NotificationPreferenceController(
    private val service: NotificationPreferenceService,
) {
    @GetMapping("/notification-preferences")
    fun get(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): NotificationPreferencesResponse = service.get(currentUser.userId).toResponse()

    @PatchMapping("/notification-preferences/{topic}")
    fun updateTopic(
        @PathVariable topic: NotificationTopic,
        @RequestBody request: UpdateNotificationTopicRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): NotificationPreferencesResponse =
        service.updateTopic(currentUser.userId, topic, request.inApp, request.email, request.sms).toResponse()

    @PatchMapping("/sms-consent")
    fun updateSmsConsent(
        @RequestBody request: UpdateSmsConsentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): NotificationPreferencesResponse = service.setSmsConsent(currentUser.userId, request.consented).toResponse()
}

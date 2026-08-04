package com.rally26.integration.googlecalendar.web

import com.rally26.common.web.CurrentUser
import com.rally26.integration.googlecalendar.application.GoogleCalendarService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/integrations/google-calendar")
class GoogleCalendarController(
    private val service: GoogleCalendarService,
) {
    @GetMapping
    fun overview(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): GoogleCalendarOverviewResponse = service.overview(currentUser).toResponse()

    @GetMapping("/calendars")
    fun calendars(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<GoogleCalendarDescriptorResponse> = service.listCalendars(currentUser).map { it.toResponse() }

    @PutMapping("/selection")
    fun select(
        @RequestBody request: SelectGoogleCalendarRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): GoogleCalendarSettingResponse = service.selectCalendar(request.calendarId, currentUser).toResponse()

    @DeleteMapping("/selection")
    fun clear(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): GoogleCalendarSettingResponse? = service.clearCalendar(currentUser)?.toResponse()

    @GetMapping("/event-mappings")
    fun mappings(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<GoogleCalendarEventMappingResponse> = service.listMappings(currentUser).map { it.toResponse() }
}

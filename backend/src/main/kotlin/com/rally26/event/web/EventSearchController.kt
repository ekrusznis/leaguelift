package com.rally26.event.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.event.application.EventSearchService
import com.rally26.event.application.EventService
import com.rally26.event.domain.EventListCriteria
import com.rally26.event.domain.EventListSort
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class EventSearchController(
    private val searchService: EventSearchService,
    private val eventService: EventService,
) {
    @GetMapping("/organizations/{organizationId}/events/search")
    fun organization(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) toDate: LocalDate?,
        @RequestParam(defaultValue = "DATE_ASC") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<EventResponse> =
        response(
            organizationId,
            page,
            size,
            criteria(q, eventType, status, from, to, fromDate, toDate, sort),
        ) { offset, limit, listCriteria ->
            searchService.organization(organizationId, listCriteria, currentUser, offset, limit)
        }

    @GetMapping("/teams/{teamId}/events/search")
    fun team(
        @PathVariable teamId: UUID,
        @RequestParam organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) toDate: LocalDate?,
        @RequestParam(defaultValue = "DATE_ASC") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<EventResponse> =
        response(
            organizationId,
            page,
            size,
            criteria(q, eventType, status, from, to, fromDate, toDate, sort),
        ) { offset, limit, listCriteria ->
            searchService.team(organizationId, teamId, listCriteria, currentUser, offset, limit)
        }

    @GetMapping("/tournaments/{tournamentId}/events/search")
    fun tournament(
        @PathVariable tournamentId: UUID,
        @RequestParam organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) toDate: LocalDate?,
        @RequestParam(defaultValue = "DATE_ASC") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<EventResponse> =
        response(
            organizationId,
            page,
            size,
            criteria(q, eventType, status, from, to, fromDate, toDate, sort),
        ) { offset, limit, listCriteria ->
            searchService.tournament(organizationId, tournamentId, listCriteria, currentUser, offset, limit)
        }

    @GetMapping("/households/{householdId}/events/search")
    fun household(
        @PathVariable householdId: UUID,
        @RequestParam organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) toDate: LocalDate?,
        @RequestParam(defaultValue = "DATE_ASC") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<EventResponse> =
        response(
            organizationId,
            page,
            size,
            criteria(q, eventType, status, from, to, fromDate, toDate, sort),
        ) { offset, limit, listCriteria ->
            searchService.household(organizationId, householdId, listCriteria, currentUser, offset, limit)
        }

    @GetMapping("/participants/{participantId}/events/search")
    fun participant(
        @PathVariable participantId: UUID,
        @RequestParam organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) toDate: LocalDate?,
        @RequestParam(defaultValue = "DATE_ASC") sort: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<EventResponse> =
        response(
            organizationId,
            page,
            size,
            criteria(q, eventType, status, from, to, fromDate, toDate, sort),
        ) { offset, limit, listCriteria ->
            searchService.participant(organizationId, participantId, listCriteria, currentUser, offset, limit)
        }

    private fun response(
        organizationId: UUID,
        page: Int,
        size: Int,
        criteria: EventListCriteria,
        query: (Int, Int, EventListCriteria) -> Pair<List<com.rally26.event.domain.Event>, Long>,
    ): PageResponse<EventResponse> {
        validatePage(page, size)
        val (events, total) = query(page * size, size, criteria)
        return PageResponse(
            events.map { it.toResponse(eventService.displayTitleFor(it, organizationId)) },
            page,
            size,
            total,
        )
    }

    private fun criteria(
        q: String?,
        eventType: String?,
        status: String?,
        from: Instant?,
        to: Instant?,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        sort: String,
    ): EventListCriteria =
        EventListCriteria(
            keyword = q,
            eventType = enumValueOrNull<EventType>(eventType, "event type"),
            status = enumValueOrNull<EventStatus>(status, "event status"),
            from = from,
            to = to,
            fromDate = fromDate,
            toDate = toDate,
            sort = enumValue<EventListSort>(sort, "event sort"),
        )

    private fun validatePage(
        page: Int,
        size: Int,
    ) {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        label: String,
    ): T =
        runCatching { enumValueOf<T>(value.uppercase()) }
            .getOrElse { throw ValidationException("Unknown $label.") }

    private inline fun <reified T : Enum<T>> enumValueOrNull(
        value: String?,
        label: String,
    ): T? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { enumValue<T>(it, label) }
}

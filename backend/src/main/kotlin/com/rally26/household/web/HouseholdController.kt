package com.rally26.household.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.household.application.HouseholdService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/households")
class HouseholdController(private val householdService: HouseholdService) {

    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<HouseholdResponse> {
        val offset = page * size
        val items = householdService.list(organizationId, currentUser, offset, size).map { it.toResponse() }
        val total = householdService.count(organizationId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateHouseholdRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<HouseholdResponse> {
        val household = householdService.create(
            organizationId, request.displayName, request.contactEmail, request.contactPhone, request.notes, currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(household.toResponse())
    }

    @GetMapping("/{householdId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdResponse = householdService.get(organizationId, householdId, currentUser).toResponse()

    @PatchMapping("/{householdId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: UpdateHouseholdRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): HouseholdResponse = householdService.update(
        organizationId, householdId, request.displayName, request.contactEmail, request.contactPhone, request.notes, currentUser,
        request.emailRemindersOptOut, request.smsRemindersOptIn,
    ).toResponse()

    @GetMapping("/{householdId}/adults")
    fun listAdults(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<HouseholdAdultResponse> = householdService.listAdults(organizationId, householdId, currentUser).map { it.toResponse() }

    @PostMapping("/{householdId}/adults")
    fun addAdult(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: AddAdultRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<HouseholdAdultResponse> {
        val adult = householdService.addAdult(
            organizationId, householdId, request.firstName, request.lastName,
            request.email, request.phone, request.relationship, request.isPrimary, currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(adult.toResponse())
    }

    @DeleteMapping("/{householdId}/adults/{adultId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeAdult(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @PathVariable adultId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = householdService.removeAdult(organizationId, householdId, adultId, currentUser)
}

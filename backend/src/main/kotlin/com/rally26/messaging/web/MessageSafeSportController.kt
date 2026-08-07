package com.rally26.messaging.web

import com.rally26.common.web.CurrentUser
import com.rally26.messaging.application.MessageSafeSportService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/messaging/safe-sport-policy")
class MessageSafeSportPolicyController(
    private val service: MessageSafeSportService,
) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.getPolicy(organizationId, currentUser).toResponse()

    @PatchMapping
    fun recordReview(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: UpdateMessageSafeSportPolicyRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service
        .recordReview(
            organizationId,
            request.reviewStatus,
            request.athleteMessagingEnabled,
            request.reviewReference,
            currentUser,
        ).toResponse()
}

@RestController
@RequestMapping("/api/v1/me/messaging")
class MyMessageContactRestrictionController(
    private val service: MessageSafeSportService,
) {
    @GetMapping("/guardian-participants")
    fun participants(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.listGuardianParticipants(currentUser).map { it.toResponse() }

    @GetMapping("/contact-restrictions")
    fun restrictions(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.listMyRestrictions(currentUser).map { it.toResponse() }

    @PostMapping("/contact-restrictions")
    fun create(
        @Valid @RequestBody request: CreateMessageContactRestrictionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<MessageContactRestrictionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service.createRestriction(request.organizationId, request.participantId, request.kind, request.note, currentUser).toResponse(),
        )

    @PostMapping("/contact-restrictions/{restrictionId}/lift")
    fun lift(
        @PathVariable restrictionId: UUID,
        @Valid @RequestBody request: LiftMessageContactRestrictionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.liftRestriction(restrictionId, request.note, currentUser).toResponse()
}

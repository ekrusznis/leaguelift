package com.leaguelift.seasonrollover.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.seasonrollover.application.SeasonRolloverService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/season-rollovers")
class SeasonRolloverController(private val service: SeasonRolloverService) {

	@PostMapping("/preview")
	fun preview(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: SeasonRolloverPreviewRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SeasonRolloverPreviewResponse = service.preview(organizationId, request.toCommand(), currentUser).toResponse()

	@PostMapping("/execute")
	fun execute(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: SeasonRolloverExecuteRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): SeasonRolloverResultResponse = service.execute(
		organizationId,
		request.toCommand(),
		request.expectedConfirmationHash,
		currentUser,
	).toResponse()
}

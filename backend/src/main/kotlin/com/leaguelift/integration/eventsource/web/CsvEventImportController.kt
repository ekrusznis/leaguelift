package com.leaguelift.integration.eventsource.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.integration.eventsource.application.CsvEventImportService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** CSV schedule import (Phase 12 slice 2, ADR-032) — a one-time bulk action, not a persisted connection. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/events")
class CsvEventImportController(
	private val csvEventImportService: CsvEventImportService,
) {

	@PostMapping("/csv-import")
	fun importCsv(
		@PathVariable organizationId: UUID,
		@Valid @RequestBody request: CsvImportRequest,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): CsvImportResponse =
		csvEventImportService.import(organizationId, request.teamId, request.timezone, request.csvContent, currentUser).toResponse()
}

package com.leaguelift.integration.eventsource.web

import com.leaguelift.integration.eventsource.application.CsvImportResult
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CsvImportRequest(
	val teamId: UUID?,
	@field:NotBlank val timezone: String,
	@field:NotBlank val csvContent: String,
)

data class CsvImportRowErrorResponse(val rowNumber: Int, val message: String)

data class CsvImportResponse(
	val createdCount: Int,
	val updatedCount: Int,
	val unchangedCount: Int,
	val errors: List<CsvImportRowErrorResponse>,
)

fun CsvImportResult.toResponse() = CsvImportResponse(
	createdCount = createdCount,
	updatedCount = updatedCount,
	unchangedCount = unchangedCount,
	errors = errors.map { CsvImportRowErrorResponse(it.rowNumber, it.message) },
)

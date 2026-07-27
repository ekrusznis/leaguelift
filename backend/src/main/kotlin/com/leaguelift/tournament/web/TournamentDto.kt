package com.leaguelift.tournament.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.tournament.domain.Tournament
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateTournamentRequest(
    @field:Size(min = 1, max = 120)
    val name: String,
    @field:Size(min = 1, max = 60)
    val sport: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:Size(max = 200)
    val location: String? = null,
    @field:Email
    val contactEmail: String? = null,
)

data class UpdateTournamentRequest(
    @field:Size(min = 1, max = 120)
    val name: String? = null,
    @field:Size(min = 1, max = 60)
    val sport: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:Size(max = 200)
    val location: String? = null,
    @field:Email
    val contactEmail: String? = null,
)

data class TournamentResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String?,
    val status: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val location: String?,
    val contactEmail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Tournament.toResponse() = TournamentResponse(
    id = id,
    organizationId = organizationId,
    name = name,
    sport = sport,
    status = status.name,
    startDate = startDate,
    endDate = endDate,
    location = location,
    contactEmail = contactEmail,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

typealias TournamentPageResponse = PageResponse<TournamentResponse>

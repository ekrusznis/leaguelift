package com.rally26.team.web

import com.rally26.common.web.PageResponse
import com.rally26.team.domain.Team
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateTeamRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 120)
    val name: String,
    @field:NotBlank
    @field:Size(min = 1, max = 60)
    val sport: String,
    @field:Size(max = 60)
    val season: String? = null,
    @field:Email
    val contactEmail: String? = null,
)

data class UpdateTeamRequest(
    @field:Size(min = 1, max = 120)
    val name: String? = null,
    @field:Size(min = 1, max = 60)
    val sport: String? = null,
    @field:Size(max = 60)
    val season: String? = null,
    @field:Email
    val contactEmail: String? = null,
)

data class TeamResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String,
    val season: String?,
    val status: String,
    val contactEmail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Team.toResponse() = TeamResponse(
    id = id,
    organizationId = organizationId,
    name = name,
    sport = sport,
    season = season,
    status = status.name,
    contactEmail = contactEmail,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

typealias TeamPageResponse = PageResponse<TeamResponse>

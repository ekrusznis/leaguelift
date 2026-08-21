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
    @field:Size(max = 60)
    val ageGroup: String? = null,
    val genderCategory: String? = null,
    @field:Size(max = 60)
    val level: String? = null,
    /** Required (and only meaningful) when [sport] is "OTHER" — the org's real sport name, e.g. "Ultimate Frisbee". */
    @field:Size(max = 60)
    val sportOtherLabel: String? = null,
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
    @field:Size(max = 60)
    val ageGroup: String? = null,
    val genderCategory: String? = null,
    @field:Size(max = 60)
    val level: String? = null,
    @field:Size(max = 60)
    val sportOtherLabel: String? = null,
)

data class TeamResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String,
    val season: String?,
    val status: String,
    val contactEmail: String?,
    val timezoneOverride: String?,
    val ageGroup: String?,
    val genderCategory: String?,
    val level: String?,
    val primaryColor: String,
    val secondaryColor: String,
    val sportOtherLabel: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Team.toResponse() =
    TeamResponse(
        id = id,
        organizationId = organizationId,
        name = name,
        sport = sport.name,
        season = season,
        status = status.name,
        contactEmail = contactEmail,
        timezoneOverride = timezoneOverride,
        ageGroup = ageGroup,
        genderCategory = genderCategory?.name,
        level = level,
        primaryColor = resolvedPrimaryColor,
        secondaryColor = resolvedSecondaryColor,
        sportOtherLabel = sportOtherLabel,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** [timezone] null explicitly clears the override back to "inherit organization default." */
data class UpdateTeamTimezoneRequest(
    val timezone: String? = null,
)

/** [primaryColor]/[secondaryColor] null explicitly clears that color back to Rally26's default brand color. */
data class UpdateTeamColorsRequest(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
)

typealias TeamPageResponse = PageResponse<TeamResponse>

package com.rally26.team.domain

import com.rally26.team.domain.Sport
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamTest {
    private fun sampleTeam(
        primaryColor: String? = null,
        secondaryColor: String? = null,
    ) = Team(
        id = UUID.randomUUID(),
        organizationId = UUID.randomUUID(),
        name = "Riverside U12 Blue",
        sport = Sport.SOCCER,
        season = "Fall 2026",
        status = TeamStatus.ACTIVE,
        contactEmail = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
    )

    @Test
    fun `resolved colors fall back to the Rally26 default brand colors when unset`() {
        val team = sampleTeam()

        assertEquals(Team.DEFAULT_PRIMARY_COLOR, team.resolvedPrimaryColor)
        assertEquals(Team.DEFAULT_SECONDARY_COLOR, team.resolvedSecondaryColor)
    }

    @Test
    fun `resolved colors use the team's own override when set`() {
        val team = sampleTeam(primaryColor = "#112233", secondaryColor = "#445566")

        assertEquals("#112233", team.resolvedPrimaryColor)
        assertEquals("#445566", team.resolvedSecondaryColor)
    }

    @Test
    fun `hex color pattern accepts a 6-digit hex value and rejects everything else`() {
        assertEquals(true, Team.HEX_COLOR_PATTERN.matches("#0B1F33"))
        assertEquals(true, Team.HEX_COLOR_PATTERN.matches("#abcdef"))
        assertEquals(false, Team.HEX_COLOR_PATTERN.matches("navy"))
        assertEquals(false, Team.HEX_COLOR_PATTERN.matches("#FFF"))
        assertEquals(false, Team.HEX_COLOR_PATTERN.matches("0B1F33"))
    }
}

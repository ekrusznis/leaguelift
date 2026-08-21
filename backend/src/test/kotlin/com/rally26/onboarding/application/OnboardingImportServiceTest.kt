package com.rally26.onboarding.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.AdultStatus
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.onboarding.domain.OnboardingPreviewAction
import com.rally26.onboarding.domain.OnboardingRecordType
import com.rally26.onboarding.persistence.OnboardingImportIdentity
import com.rally26.onboarding.persistence.OnboardingImportIdentityRepository
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnboardingImportServiceTest {
    private val identityRepository = mockk<OnboardingImportIdentityRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service =
        OnboardingImportService(
            identityRepository,
            teamRepository,
            householdRepository,
            participantRepository,
            membershipService,
            auditService,
            ObjectMapper(),
        )

    private val organizationId = UUID.randomUUID()
    private val user = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    private fun allowManager() {
        every { membershipService.requireManagerRole(organizationId, user) } returns mockk<OrganizationMembership>()
    }

    private fun emptyMatches() {
        every { identityRepository.find(organizationId, any(), any()) } returns null
        every { identityRepository.findByEntity(organizationId, any(), any()) } returns null
        every { teamRepository.findNameMatches(organizationId, any()) } returns emptyList()
        every { householdRepository.findContactEmailMatches(organizationId, any()) } returns emptyList()
        every { householdRepository.findActiveAdultEmailMatches(organizationId, any()) } returns emptyList()
    }

    @Test
    fun `preview validates a complete dependency-ordered onboarding file without writing`() {
        allowManager()
        emptyMatches()
        val csv =
            """
            record_type,external_id,household_external_id,team_external_id,display_name,name,sport,season,first_name,last_name,email,phone,relationship,is_primary,date_of_birth,contact_email,contact_phone,notes
            TEAM,team-blue,,,,U14 Blue,Volleyball,2026-2027,,,,,,,,coach@example.com,,
            HOUSEHOLD,household-smith,,,Smith Family,,,,,,,,,,,parent@example.com,555-555-0101,
            GUARDIAN,guardian-jamie,household-smith,,,,,,Jamie,Smith,jamie@example.com,555-555-0101,Parent,true,,,,
            PARTICIPANT,participant-riley,household-smith,team-blue,,,,,Riley,Smith,,,,,2012-09-15,,,
            """.trimIndent()

        val preview = service.preview(organizationId, "pilot.csv", csv, user)

        assertEquals(4, preview.totalRows)
        assertEquals(4, preview.validRows)
        assertEquals(0, preview.errorRows)
        assertEquals(4, preview.createCount)
        assertTrue(preview.rows.all { it.action == OnboardingPreviewAction.CREATE })
        verify(exactly = 0) { teamRepository.insert(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `preview rejects duplicate external identities in the same file`() {
        allowManager()
        emptyMatches()
        val csv =
            """
            record_type,external_id,name,sport
            TEAM,team-blue,U14 Blue,Volleyball
            TEAM,team-blue,U14 Blue,Volleyball
            """.trimIndent()

        val preview = service.preview(organizationId, null, csv, user)

        assertEquals(2, preview.errorRows)
        assertTrue(preview.rows.all { "Duplicate record_type/external_id" in it.errors.single() })
    }

    @Test
    fun `preview refuses to choose between ambiguous natural matches`() {
        allowManager()
        every { identityRepository.find(organizationId, any(), any()) } returns null
        val now = Instant.now()
        every { teamRepository.findNameMatches(organizationId, "U14 Blue") } returns
            listOf(
                Team(UUID.randomUUID(), organizationId, "U14 Blue", Sport.VOLLEYBALL, null, TeamStatus.ACTIVE, null, now, now),
                Team(UUID.randomUUID(), organizationId, "U14 Blue", Sport.VOLLEYBALL, null, TeamStatus.ACTIVE, null, now, now),
            )

        val preview =
            service.preview(
                organizationId,
                null,
                "record_type,external_id,name,sport\nTEAM,team-blue,U14 Blue,Volleyball\n",
                user,
            )

        assertEquals(1, preview.errorRows)
        assertTrue(
            preview.rows
                .single()
                .errors
                .single()
                .contains("More than one existing team"),
        )
    }

    @Test
    fun `preview propagates an invalid referenced team to the participant row`() {
        allowManager()
        emptyMatches()
        val csv =
            """
            record_type,external_id,household_external_id,team_external_id,display_name,name,sport,first_name,last_name
            TEAM,team-blue,,,,U14 Blue,,,
            HOUSEHOLD,household-smith,,,Smith Family,,,,
            PARTICIPANT,participant-riley,household-smith,team-blue,,,,Riley,Smith
            """.trimIndent()

        val preview = service.preview(organizationId, null, csv, user)

        assertEquals(2, preview.errorRows)
        assertTrue(
            preview.rows
                .last()
                .errors
                .any { it.contains("team_external_id") && it.contains("has errors") },
        )
    }

    @Test
    fun `preview rejects an existing guardian email that belongs to another household`() {
        allowManager()
        every { identityRepository.find(organizationId, any(), any()) } returns null
        every { householdRepository.findContactEmailMatches(organizationId, any()) } returns emptyList()
        val now = Instant.now()
        every { householdRepository.findActiveAdultEmailMatches(organizationId, "jamie@example.com") } returns
            listOf(
                HouseholdAdult(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    organizationId,
                    "Jamie",
                    "Smith",
                    "jamie@example.com",
                    null,
                    "Parent",
                    true,
                    AdultStatus.ACTIVE,
                    now,
                    now,
                ),
            )
        val csv =
            """
            record_type,external_id,household_external_id,display_name,first_name,last_name,email
            HOUSEHOLD,household-new,,New Household,,,
            GUARDIAN,guardian-jamie,household-new,,Jamie,Smith,jamie@example.com
            """.trimIndent()

        val preview = service.preview(organizationId, null, csv, user)

        assertEquals(1, preview.errorRows)
        assertTrue(
            preview.rows
                .last()
                .errors
                .single()
                .contains("already belongs to another household"),
        )
    }

    @Test
    fun `execute requires the exact file that was previewed`() {
        allowManager()
        emptyMatches()

        assertFailsWith<ValidationException> {
            service.execute(
                organizationId,
                "pilot.csv",
                "record_type,external_id,name,sport\nTEAM,team-blue,U14 Blue,Volleyball\n",
                "not-the-preview-hash",
                user,
            )
        }
    }

    @Test
    fun `execute aborts when a matched record was concurrently bound to another external id`() {
        allowManager()
        val now = Instant.now()
        val team = Team(UUID.randomUUID(), organizationId, "U14 Blue", Sport.VOLLEYBALL, null, TeamStatus.ACTIVE, null, now, now)
        every { teamRepository.findById(team.id, organizationId) } returns team
        every { identityRepository.find(organizationId, OnboardingRecordType.TEAM, "team-blue") } returns null
        every { identityRepository.findByEntity(organizationId, OnboardingRecordType.TEAM, team.id) } returns null
        every { teamRepository.findNameMatches(organizationId, "U14 Blue") } returns listOf(team)
        every {
            identityRepository.insert(organizationId, OnboardingRecordType.TEAM, "team-blue", team.id, user.userId)
        } returns
            OnboardingImportIdentity(
                UUID.randomUUID(),
                organizationId,
                OnboardingRecordType.TEAM,
                "other-source-id",
                team.id,
                user.userId,
                now,
            )
        val csv = "record_type,external_id,name,sport\nTEAM,team-blue,U14 Blue,Volleyball\n"
        val preview = service.preview(organizationId, "pilot.csv", csv, user)

        assertFailsWith<ConflictException> {
            service.execute(organizationId, "pilot.csv", csv, preview.contentHash, user)
        }
    }

    @Test
    fun `execute creates a team and persists its stable external identity`() {
        allowManager()
        emptyMatches()
        val now = Instant.now()
        val team = Team(UUID.randomUUID(), organizationId, "U14 Blue", Sport.VOLLEYBALL, "2026-2027", TeamStatus.ACTIVE, null, now, now)
        val csv = "record_type,external_id,name,sport,season\nTEAM,team-blue,U14 Blue,Volleyball,2026-2027\n"
        val preview = service.preview(organizationId, "pilot.csv", csv, user)
        every {
            teamRepository.insert(organizationId, "U14 Blue", Sport.VOLLEYBALL, "2026-2027", null, null, null, null)
        } returns team
        every {
            identityRepository.insert(organizationId, OnboardingRecordType.TEAM, "team-blue", team.id, user.userId)
        } returns
            OnboardingImportIdentity(
                UUID.randomUUID(),
                organizationId,
                OnboardingRecordType.TEAM,
                "team-blue",
                team.id,
                user.userId,
                now,
            )
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.execute(organizationId, "pilot.csv", csv, preview.contentHash, user)

        assertEquals(1, result.createdCount)
        assertEquals(team.id, result.entities.single().entityId)
        assertTrue(result.errors.isEmpty())
        verify(exactly = 1) {
            identityRepository.insert(organizationId, OnboardingRecordType.TEAM, "team-blue", team.id, user.userId)
        }
    }
}

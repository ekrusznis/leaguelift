package com.rally26.platformadmin.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.platformadmin.domain.PlatformOrganizationDetail
import com.rally26.platformadmin.domain.PlatformSupportAccess
import com.rally26.platformadmin.domain.PlatformSupportAccessStatus
import com.rally26.platformadmin.persistence.PlatformAdminConsoleRepository
import com.rally26.platformadmin.persistence.PlatformSupportAccessRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformAdminConsoleServiceTest {
    private val authorizationService = mockk<AuthorizationService>()
    private val consoleRepository = mockk<PlatformAdminConsoleRepository>()
    private val supportAccessRepository = mockk<PlatformSupportAccessRepository>()
    private val auditService = mockk<AuditService>()
    private val now = Instant.parse("2026-07-31T16:00:00Z")
    private val service =
        PlatformAdminConsoleService(
            authorizationService,
            consoleRepository,
            supportAccessRepository,
            auditService,
            ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
    private val admin = CurrentUser(UUID.randomUUID(), "employee@rally26.com", "Support Employee", platformAdministrator = true)
    private val organizationId = UUID.randomUUID()

    @Test
    fun `support access requires the platform support capability`() {
        every { authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_SUPPORT_ACCESS) } throws
            ForbiddenException("PLATFORM_ACCESS_DENIED", "denied")

        assertFailsWith<ForbiddenException> {
            service.startSupportAccess(admin, organizationId, "Investigate customer roster issue")
        }
    }

    @Test
    fun `support access rejects an empty or vague reason`() {
        every { authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_SUPPORT_ACCESS) } just runs

        assertFailsWith<ValidationException> {
            service.startSupportAccess(admin, organizationId, "help")
        }
    }

    @Test
    fun `starting support access is organization scoped time bounded and audited`() {
        val accessId = UUID.randomUUID()
        val access = access(accessId, organizationId, now.plusSeconds(7200))
        every { authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_SUPPORT_ACCESS) } just runs
        every { consoleRepository.findOrganization(organizationId) } returns organization(organizationId)
        every { supportAccessRepository.findActiveForAdmin(admin.userId) } returns null
        every {
            supportAccessRepository.create(
                admin.userId,
                organizationId,
                "Investigate customer roster issue",
                now,
                now.plusSeconds(7200),
            )
        } returns access
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.startSupportAccess(admin, organizationId, "  Investigate customer roster issue  ")

        assertEquals(access, result)
        verify(exactly = 1) {
            auditService.record(
                actorUserId = admin.userId,
                organizationId = organizationId,
                action = "platform.support_access.started",
                entityType = "PLATFORM_SUPPORT_ACCESS",
                entityId = accessId,
                metadataJson = match { it.contains("Investigate customer roster issue") && it.contains("2026-07-31T18:00:00Z") },
            )
        }
    }

    @Test
    fun `support access cannot be reused for another organization`() {
        val access = access(UUID.randomUUID(), organizationId, now.plusSeconds(7200))
        every { authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_SUPPORT_ACCESS) } just runs
        every { supportAccessRepository.findById(access.id) } returns access

        assertFailsWith<ForbiddenException> {
            service.requireActiveSupportAccess(admin, access.id, UUID.randomUUID())
        }
    }

    private fun access(
        id: UUID,
        orgId: UUID,
        expiresAt: Instant,
    ) = PlatformSupportAccess(
        id = id,
        platformAdminUserId = admin.userId,
        organizationId = orgId,
        organizationName = "North Jersey Volleyball Club",
        reason = "Investigate customer roster issue",
        status = PlatformSupportAccessStatus.ACTIVE,
        expiresAt = expiresAt,
        endedAt = null,
        createdAt = now,
    )

    private fun organization(id: UUID) =
        PlatformOrganizationDetail(
            organizationId = id,
            name = "North Jersey Volleyball Club",
            slug = "north-jersey-volleyball",
            organizationType = "TRAVEL_CLUB",
            status = "ACTIVE",
            contactEmail = "owner@example.com",
            contactPhone = null,
            createdAt = now,
            updatedAt = now,
            ownerNames = listOf("Owner"),
            ownerEmails = listOf("owner@example.com"),
            activeMembers = 2,
            invitedMembers = 1,
            teams = 4,
            tournaments = 0,
            households = 20,
            guardians = 25,
            participants = 30,
            events = 10,
            stores = 1,
            orders = 5,
            campaigns = 1,
            contributions = 6,
            sponsorships = 2,
            documents = 3,
            activeEventConnections = 0,
            grossVolumeMinor = 100_00,
            refundedMinor = 0,
            organizationEarningsMinor = 80_00,
        )
}

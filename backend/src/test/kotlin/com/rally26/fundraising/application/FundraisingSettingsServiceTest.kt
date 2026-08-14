package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.common.web.CurrentUser
import com.rally26.fundraising.domain.FundraisingSettings
import com.rally26.fundraising.persistence.FundraisingSettingsRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundraisingSettingsServiceTest {
    private val repository = mockk<FundraisingSettingsRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val service = FundraisingSettingsService(repository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val owner = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `missing settings row defaults to requiring owner approval`() {
        every { repository.findByOrganizationId(orgId) } returns null

        val result = service.getInternal(orgId)

        assertTrue(result.requireOwnerApproval)
    }

    @Test
    fun `owner can disable approval requirement and change is audited`() {
        val ownerMembership =
            OrganizationMembership(
                UUID.randomUUID(),
                orgId,
                owner.userId,
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        every { membershipService.requireOwnerRole(orgId, owner) } returns ownerMembership
        every { repository.findByOrganizationId(orgId) } returns FundraisingSettings.defaultFor(orgId)
        every { repository.upsert(orgId, false, owner.userId) } returns FundraisingSettings(orgId, false, owner.userId, Instant.now())

        val result = service.update(orgId, false, owner)

        assertFalse(result.requireOwnerApproval)
        verify(exactly = 1) { membershipService.requireOwnerRole(orgId, owner) }
        verify(exactly = 1) {
            auditService.record(
                actorUserId = owner.userId,
                organizationId = orgId,
                action = "fundraising.settings.updated",
                entityType = "organization_fundraising_settings",
                entityId = orgId,
                metadataJson = any(),
                summary = "Fundraising approval policy updated",
            )
        }
    }
}

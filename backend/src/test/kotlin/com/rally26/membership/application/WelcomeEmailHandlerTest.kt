package com.rally26.membership.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.notification.EmailMessage
import com.rally26.notification.EmailProvider
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WelcomeEmailHandlerTest {
    private val membershipRepository = mockk<MembershipRepository>()
    private val appUserRepository = mockk<AppUserRepository>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val emailProvider = mockk<EmailProvider>()
    private val frontendProperties = FrontendProperties(baseUrl = "https://app.rally26.test")

    private fun handlerWith(resendTemplateProperties: ResendTemplateProperties) =
        WelcomeEmailHandler(
            membershipRepository,
            appUserRepository,
            organizationRepository,
            emailProvider,
            frontendProperties,
            resendTemplateProperties,
            ObjectMapper(),
        )

    private val handler = handlerWith(ResendTemplateProperties())

    private fun eventFor(
        userId: UUID,
        organizationId: UUID,
        role: MembershipRole,
    ): OutboxEvent {
        val now = Instant.now()
        val membershipId = UUID.randomUUID()
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "organization_membership",
            aggregateId = membershipId,
            organizationId = organizationId,
            eventType = "membership.first_granted",
            schemaVersion = 1,
            payload = """{"membershipId":"$membershipId","userId":"$userId","organizationId":"$organizationId","role":"${role.name}"}""",
            status = OutboxEventStatus.PROCESSING,
            attemptCount = 1,
            availableAt = now,
            processedAt = null,
            lastError = null,
            createdAt = now,
        )
    }

    private fun activeMembership(
        organizationId: UUID,
        userId: UUID,
        role: MembershipRole,
    ) = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        userId = userId,
        role = role,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun activeUser(id: UUID) =
        AppUser(
            id = id,
            email = "new-owner@example.com",
            displayName = "New Owner",
            status = AppUserStatus.ACTIVE,
            passwordHash = "hash",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun organization(id: UUID) =
        Organization(
            id = id,
            name = "Rally26 Youth League",
            slug = "rally26-youth-league",
            organizationType = OrganizationType.RECREATIONAL_LEAGUE,
            status = OrganizationStatus.ACTIVE,
            sports = listOf("soccer"),
            contactEmail = null,
            contactPhone = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `sends a welcome email for a newly active membership`() {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        every { membershipRepository.findActiveMembership(organizationId, userId) } returns
            activeMembership(organizationId, userId, MembershipRole.OWNER)
        every { appUserRepository.findById(userId) } returns activeUser(userId)
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handler.handle(eventFor(userId, organizationId, MembershipRole.OWNER))

        verify(exactly = 1) { emailProvider.send(any()) }
        assertEquals("new-owner@example.com", messageSlot.captured.to)
        assertEquals(true, messageSlot.captured.body.contains("Rally26 Youth League"))
        assertEquals(true, messageSlot.captured.body.contains("Owner"))
        assertNull(messageSlot.captured.template)
    }

    @Test
    fun `sends via the Resend template with a role-scoped feature list when a template id is configured`() {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        every { membershipRepository.findActiveMembership(organizationId, userId) } returns
            activeMembership(organizationId, userId, MembershipRole.TEAM_ADMINISTRATOR)
        every { appUserRepository.findById(userId) } returns activeUser(userId)
        every { organizationRepository.findById(organizationId) } returns organization(organizationId)
        val messageSlot = slot<EmailMessage>()
        every { emailProvider.send(capture(messageSlot)) } just runs

        handlerWith(
            ResendTemplateProperties(welcomeId = "template-welcome"),
        ).handle(eventFor(userId, organizationId, MembershipRole.TEAM_ADMINISTRATOR))

        val template = messageSlot.captured.template
        assertEquals("template-welcome", template?.id)
        assertEquals("Rally26 Youth League", template?.variables?.get("ORG_NAME"))
        assertEquals("Team Administrator", template?.variables?.get("ROLE_LABEL"))
        assertEquals("https://app.rally26.test/dashboard", template?.variables?.get("DASHBOARD_URL"))
        val featuresHtml = template?.variables?.get("FEATURES_HTML") as String
        assertEquals(true, featuresHtml.contains("Team schedules &amp; events"))
    }

    @Test
    fun `does nothing when the membership has since been revoked`() {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        every { membershipRepository.findActiveMembership(organizationId, userId) } returns null

        handler.handle(eventFor(userId, organizationId, MembershipRole.OWNER))

        verify(exactly = 0) { emailProvider.send(any()) }
    }

    @Test
    fun `does nothing when the user can no longer be found`() {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        every { membershipRepository.findActiveMembership(organizationId, userId) } returns
            activeMembership(organizationId, userId, MembershipRole.OWNER)
        every { appUserRepository.findById(userId) } returns null

        handler.handle(eventFor(userId, organizationId, MembershipRole.OWNER))

        verify(exactly = 0) { emailProvider.send(any()) }
    }
}

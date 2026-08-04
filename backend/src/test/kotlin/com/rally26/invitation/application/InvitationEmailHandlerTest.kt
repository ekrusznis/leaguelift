package com.rally26.invitation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FrontendProperties
import com.rally26.config.ResendTemplateProperties
import com.rally26.invitation.domain.Invitation
import com.rally26.invitation.domain.InvitationStatus
import com.rally26.invitation.persistence.InvitationRepository
import com.rally26.membership.domain.MembershipRole
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
import kotlin.test.assertTrue

class InvitationEmailHandlerTest {

	private val invitationRepository = mockk<InvitationRepository>()
	private val organizationRepository = mockk<OrganizationRepository>()
	private val emailProvider = mockk<EmailProvider>()
	private val frontendProperties = FrontendProperties(baseUrl = "https://app.rally26.test")

	private fun handlerWith(resendTemplateProperties: ResendTemplateProperties) = InvitationEmailHandler(
		invitationRepository,
		organizationRepository,
		emailProvider,
		frontendProperties,
		resendTemplateProperties,
		ObjectMapper(),
	)

	private val handler = handlerWith(ResendTemplateProperties())

	private fun organization(id: UUID) = Organization(
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

	private fun outboxEventFor(invitationId: UUID): OutboxEvent {
		val now = Instant.now()
		return OutboxEvent(
			id = UUID.randomUUID(), aggregateType = "invitation", aggregateId = invitationId, organizationId = UUID.randomUUID(),
			eventType = "membership.invited", schemaVersion = 1,
			payload = """{"invitationId":"$invitationId","email":"new@example.com","role":"ADMINISTRATOR","acceptToken":"token-from-event"}""",
			status = OutboxEventStatus.PROCESSING, attemptCount = 1, availableAt = now, processedAt = null, lastError = null, createdAt = now,
		)
	}

	private fun pendingInvitation(id: UUID) = Invitation(
		id = id, organizationId = UUID.randomUUID(), email = "new@example.com", role = MembershipRole.ADMINISTRATOR,
		status = InvitationStatus.PENDING, invitedByUserId = UUID.randomUUID(), token = "real-token-value",
		expiresAt = Instant.now().plusSeconds(600), acceptedAt = null, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	@Test
	fun `sends an email with the accept link built from the payload token`() {
		val invitationId = UUID.randomUUID()
		val invitation = pendingInvitation(invitationId)
		every { invitationRepository.findById(invitationId) } returns invitation
		every { organizationRepository.findById(invitation.organizationId) } returns organization(invitation.organizationId)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(outboxEventFor(invitationId))

		verify(exactly = 1) { emailProvider.send(any()) }
		assertEquals("new@example.com", messageSlot.captured.to)
		assertTrue(messageSlot.captured.body.contains("https://app.rally26.test/auth/invitation?token=token-from-event"))
		assertTrue(messageSlot.captured.body.contains("Rally26 Youth League"))
		assertNull(messageSlot.captured.template)
	}

	@Test
	fun `sends via the Resend template when a template id is configured`() {
		val invitationId = UUID.randomUUID()
		val invitation = pendingInvitation(invitationId)
		every { invitationRepository.findById(invitationId) } returns invitation
		every { organizationRepository.findById(invitation.organizationId) } returns organization(invitation.organizationId)
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handlerWith(ResendTemplateProperties(invitationId = "template-invitation")).handle(outboxEventFor(invitationId))

		val template = messageSlot.captured.template
		assertEquals("template-invitation", template?.id)
		assertEquals("Rally26 Youth League", template?.variables?.get("ORG_NAME"))
		assertEquals("Administrator", template?.variables?.get("ROLE"))
		assertEquals(
			"https://app.rally26.test/auth/invitation?token=token-from-event",
			template?.variables?.get("ACCEPT_URL"),
		)
	}

	@Test
	fun `falls back to a generic organization name if the organization can no longer be found`() {
		val invitationId = UUID.randomUUID()
		val invitation = pendingInvitation(invitationId)
		every { invitationRepository.findById(invitationId) } returns invitation
		every { organizationRepository.findById(invitation.organizationId) } returns null
		val messageSlot = slot<EmailMessage>()
		every { emailProvider.send(capture(messageSlot)) } just runs

		handler.handle(outboxEventFor(invitationId))

		assertTrue(messageSlot.captured.body.contains("your organization"))
	}

	@Test
	fun `does nothing when the invitation no longer exists`() {
		val invitationId = UUID.randomUUID()
		every { invitationRepository.findById(invitationId) } returns null

		handler.handle(outboxEventFor(invitationId))

		verify(exactly = 0) { emailProvider.send(any()) }
	}

	@Test
	fun `does nothing when the invitation is no longer pending`() {
		val invitationId = UUID.randomUUID()
		every { invitationRepository.findById(invitationId) } returns pendingInvitation(invitationId).copy(status = InvitationStatus.REVOKED)

		handler.handle(outboxEventFor(invitationId))

		verify(exactly = 0) { emailProvider.send(any()) }
	}
}

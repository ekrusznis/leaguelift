package com.leaguelift.document.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.GuardianRelationship
import com.leaguelift.authorization.domain.GuardianRelationshipStatus
import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.document.persistence.DocumentAcknowledgmentRepository
import com.leaguelift.household.domain.Household
import com.leaguelift.household.domain.HouseholdStatus
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.media.domain.MediaAsset
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaAssignment
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import com.leaguelift.media.persistence.MediaAssetRepository
import com.leaguelift.media.persistence.MediaAssignmentRepository
import com.leaguelift.membership.application.MembershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentServiceTest {

	private val mediaAssetRepository = mockk<MediaAssetRepository>()
	private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
	private val membershipService = mockk<MembershipService>(relaxed = true)
	private val authorizationService = mockk<AuthorizationService>()
	private val householdRepository = mockk<HouseholdRepository>()
	private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
	private val documentAcknowledgmentRepository = mockk<DocumentAcknowledgmentRepository>()
	private val auditService = mockk<AuditService>(relaxed = true)

	private val service = DocumentService(
		mediaAssetRepository, mediaAssignmentRepository, membershipService, authorizationService,
		householdRepository, guardianRelationshipRepository, documentAcknowledgmentRepository, auditService,
	)

	private val orgId = UUID.randomUUID()
	private val householdId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin")

	private fun asset(id: UUID = UUID.randomUUID(), status: MediaAssetStatus = MediaAssetStatus.READY, slot: MediaUsageSlot = MediaUsageSlot.DOCUMENT) =
		MediaAsset(id, orgId, currentUser.userId, slot, "waiver.pdf", "application/pdf", "application/pdf", "key/$id", 1024L, "checksum", null, null, status, null, Instant.now(), Instant.now())

	private fun assignment(
		id: UUID = UUID.randomUUID(),
		entityType: MediaEntityType = MediaEntityType.HOUSEHOLD,
		entityId: UUID = householdId,
		assetId: UUID = UUID.randomUUID(),
		status: PublicationStatus = PublicationStatus.APPROVED,
	) = MediaAssignment(id, orgId, assetId, entityType, entityId, MediaUsageSlot.DOCUMENT, status, Visibility.HOUSEHOLD_PRIVATE, "Waiver", Instant.now(), Instant.now())

	private fun household() = Household(householdId, orgId, "Smith Family", null, null, null, false, false, HouseholdStatus.ACTIVE, Instant.now(), Instant.now())

	@Test
	fun `assignToOrganization rejects an asset that is not READY`() {
		val pending = asset(status = MediaAssetStatus.PENDING_UPLOAD)
		every { mediaAssetRepository.findById(pending.id, orgId) } returns pending

		assertFailsWith<ValidationException> {
			service.assignToOrganization(orgId, pending.id, "Handbook", currentUser)
		}
	}

	@Test
	fun `assignToOrganization rejects an asset uploaded for a different slot`() {
		val logoAsset = asset(slot = MediaUsageSlot.LOGO)
		every { mediaAssetRepository.findById(logoAsset.id, orgId) } returns logoAsset

		assertFailsWith<ValidationException> {
			service.assignToOrganization(orgId, logoAsset.id, "Handbook", currentUser)
		}
	}

	@Test
	fun `assignToHousehold inserts a HOUSEHOLD-entity DOCUMENT assignment without retiring anything`() {
		val doc = asset()
		every { mediaAssetRepository.findById(doc.id, orgId) } returns doc
		every { householdRepository.findById(householdId, orgId) } returns household()
		val inserted = assignment(assetId = doc.id)
		every {
			mediaAssignmentRepository.insert(orgId, doc.id, MediaEntityType.HOUSEHOLD, householdId, MediaUsageSlot.DOCUMENT, PublicationStatus.APPROVED, Visibility.HOUSEHOLD_PRIVATE, "Waiver")
		} returns inserted

		val result = service.assignToHousehold(orgId, householdId, doc.id, "Waiver", currentUser)

		assertEquals(inserted.id, result.id)
		verify(exactly = 0) { mediaAssignmentRepository.retire(any(), any()) }
	}

	@Test
	fun `assignToAllHouseholds creates one assignment per household sharing the same asset`() {
		val doc = asset()
		val householdA = household()
		val householdB = Household(UUID.randomUUID(), orgId, "Jones Family", null, null, null, false, false, HouseholdStatus.ACTIVE, Instant.now(), Instant.now())
		every { mediaAssetRepository.findById(doc.id, orgId) } returns doc
		every { householdRepository.findAll(orgId, 0, 500) } returns listOf(householdA, householdB)
		every { mediaAssignmentRepository.insert(orgId, doc.id, MediaEntityType.HOUSEHOLD, householdA.id, MediaUsageSlot.DOCUMENT, any(), any(), any()) } returns assignment(entityId = householdA.id, assetId = doc.id)
		every { mediaAssignmentRepository.insert(orgId, doc.id, MediaEntityType.HOUSEHOLD, householdB.id, MediaUsageSlot.DOCUMENT, any(), any(), any()) } returns assignment(entityId = householdB.id, assetId = doc.id)

		val result = service.assignToAllHouseholds(orgId, doc.id, "Season Waiver", currentUser)

		assertEquals(2, result.size)
	}

	@Test
	fun `listOrganizationDocuments filters to the DOCUMENT usage slot only`() {
		val logoAssignment = assignment(entityType = MediaEntityType.ORGANIZATION, entityId = orgId).copy(usageSlot = MediaUsageSlot.LOGO)
		val docAssignment = assignment(entityType = MediaEntityType.ORGANIZATION, entityId = orgId)
		every { mediaAssignmentRepository.listActive(MediaEntityType.ORGANIZATION, orgId) } returns listOf(logoAssignment, docAssignment)

		val result = service.listOrganizationDocuments(orgId, currentUser)

		assertEquals(listOf(docAssignment.id), result.map { it.id })
	}

	@Test
	fun `listHouseholdDocuments denies a caller with no household access`() {
		every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, any()) } returns false

		assertFailsWith<ForbiddenException> {
			service.listHouseholdDocuments(orgId, householdId, currentUser)
		}
	}

	@Test
	fun `removeDocument retires the assignment and never archives the asset`() {
		val a = assignment()
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a
		every { mediaAssignmentRepository.retire(a.id, orgId) } returns 1

		service.removeDocument(orgId, a.id, currentUser)

		verify(exactly = 1) { mediaAssignmentRepository.retire(a.id, orgId) }
		verify(exactly = 0) { mediaAssetRepository.archive(any(), any()) }
	}

	@Test
	fun `removeDocument 404s for an assignment that is not a document`() {
		val logoAssignment = assignment().copy(usageSlot = MediaUsageSlot.LOGO)
		every { mediaAssignmentRepository.findById(logoAssignment.id, orgId) } returns logoAssignment

		assertFailsWith<NotFoundException> {
			service.removeDocument(orgId, logoAssignment.id, currentUser)
		}
	}

	@Test
	fun `acknowledge rejects a caller who is not an active guardian of the household`() {
		val a = assignment()
		every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns null

		assertFailsWith<ForbiddenException> {
			service.acknowledge(orgId, householdId, a.id, currentUser)
		}
	}

	@Test
	fun `acknowledge records a new acknowledgment for a real guardian`() {
		val a = assignment()
		val relationship = GuardianRelationship(UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), currentUser.userId, GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now())
		every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns relationship
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a
		every { documentAcknowledgmentRepository.findFor(a.id, relationship.householdAdultId) } returns null
		val recorded = com.leaguelift.document.domain.DocumentAcknowledgment(UUID.randomUUID(), orgId, a.id, householdId, relationship.householdAdultId, currentUser.userId, Instant.now(), Instant.now())
		every {
			documentAcknowledgmentRepository.insert(orgId, a.id, householdId, relationship.householdAdultId, currentUser.userId)
		} returns recorded

		val result = service.acknowledge(orgId, householdId, a.id, currentUser)

		assertEquals(recorded.id, result.id)
	}

	@Test
	fun `acknowledge is idempotent — returns the existing record instead of inserting again`() {
		val a = assignment()
		val relationship = GuardianRelationship(UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), currentUser.userId, GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now())
		every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns relationship
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a
		val existing = com.leaguelift.document.domain.DocumentAcknowledgment(UUID.randomUUID(), orgId, a.id, householdId, relationship.householdAdultId, currentUser.userId, Instant.now(), Instant.now())
		every { documentAcknowledgmentRepository.findFor(a.id, relationship.householdAdultId) } returns existing

		val result = service.acknowledge(orgId, householdId, a.id, currentUser)

		assertEquals(existing.id, result.id)
		verify(exactly = 0) { documentAcknowledgmentRepository.insert(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `listAcknowledgments allows a guardian of the household to view who has signed`() {
		val a = assignment()
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a
		every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, any()) } returns true
		every { documentAcknowledgmentRepository.listForAssignment(a.id) } returns emptyList()

		val result = service.listAcknowledgments(orgId, a.id, currentUser)

		assertEquals(emptyList(), result)
	}

	@Test
	fun `listAcknowledgments denies a caller with no household access`() {
		val a = assignment()
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a
		every { authorizationService.hasHouseholdCapability(orgId, householdId, currentUser, any()) } returns false

		assertFailsWith<ForbiddenException> {
			service.listAcknowledgments(orgId, a.id, currentUser)
		}
	}

	@Test
	fun `acknowledge rejects a document that belongs to a different household`() {
		val otherHouseholdId = UUID.randomUUID()
		val a = assignment(entityId = otherHouseholdId)
		val relationship = GuardianRelationship(UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), currentUser.userId, GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now())
		every { guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) } returns relationship
		every { mediaAssignmentRepository.findById(a.id, orgId) } returns a

		assertFailsWith<ValidationException> {
			service.acknowledge(orgId, householdId, a.id, currentUser)
		}
	}
}

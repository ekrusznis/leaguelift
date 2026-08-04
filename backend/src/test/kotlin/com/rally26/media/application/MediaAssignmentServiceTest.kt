package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
import com.rally26.publicpage.domain.PageStatus
import com.rally26.publicpage.domain.PageType
import com.rally26.publicpage.domain.PublicPage
import com.rally26.publicpage.persistence.PublicPageRepository
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

class MediaAssignmentServiceTest {

	private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
	private val mediaAssetRepository = mockk<MediaAssetRepository>()
	private val publicPageRepository = mockk<PublicPageRepository>()
	private val membershipService = mockk<MembershipService>()
	private val mediaEntityAccessService = mockk<MediaEntityAccessService>()
	private val auditService = mockk<AuditService>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = MediaAssignmentService(
		mediaAssignmentRepository, mediaAssetRepository, publicPageRepository, membershipService, mediaEntityAccessService, auditService, outboxWriter,
	)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	@Test
	fun `assign requires manager role`() {
		val asset = readyAsset()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset
		every { publicPageRepository.findByEntityId(orgId) } returns null
		every { mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO) } returns null
		every { mediaAssignmentRepository.insert(any(), any(), any(), any(), any(), any(), any(), any()) } returns sampleAssignment(asset.id)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, asset.id, null, currentUser)

		verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
	}

	@Test
	fun `assign rejects an asset that is not READY`() {
		val asset = readyAsset().copy(status = MediaAssetStatus.PENDING_UPLOAD)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset

		assertFailsWith<ValidationException> {
			service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, asset.id, null, currentUser)
		}
	}

	@Test
	fun `assign throws NotFoundException for an unknown asset`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(any(), orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, UUID.randomUUID(), null, currentUser)
		}
	}

	@Test
	fun `assign retires the prior active assignment and archives its asset when replacing`() {
		val newAsset = readyAsset()
		val previous = sampleAssignment(UUID.randomUUID())
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(newAsset.id, orgId) } returns newAsset
		every { publicPageRepository.findByEntityId(orgId) } returns null
		every { mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO) } returns previous
		every { mediaAssignmentRepository.retire(previous.id, orgId) } returns 1
		every { mediaAssetRepository.archive(previous.assetId, orgId) } returns 1
		every { mediaAssignmentRepository.insert(any(), any(), any(), any(), any(), any(), any(), any()) } returns sampleAssignment(newAsset.id)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, newAsset.id, null, currentUser)

		verify(exactly = 1) { mediaAssignmentRepository.retire(previous.id, orgId) }
		verify(exactly = 1) { mediaAssetRepository.archive(previous.assetId, orgId) }
	}

	@Test
	fun `assign computes PUBLIC visibility when the org's public page is published`() {
		val asset = readyAsset()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset
		every { publicPageRepository.findByEntityId(orgId) } returns publishedOrgPage()
		every { mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO) } returns null
		every {
			mediaAssignmentRepository.insert(orgId, asset.id, MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO, PublicationStatus.PUBLISHED, Visibility.PUBLIC, null)
		} returns sampleAssignment(asset.id, visibility = Visibility.PUBLIC, publicationStatus = PublicationStatus.PUBLISHED)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		val result = service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, asset.id, null, currentUser)

		assertEquals(Visibility.PUBLIC, result.visibility)
		verify(exactly = 1) { outboxWriter.write("media_assignment", result.id, orgId, "media.assignment.published", any()) }
	}

	@Test
	fun `assign computes ORGANIZATION_PRIVATE visibility when there is no published public page`() {
		val asset = readyAsset()
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset
		every { publicPageRepository.findByEntityId(orgId) } returns null
		every { mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO) } returns null
		every {
			mediaAssignmentRepository.insert(orgId, asset.id, MediaEntityType.ORGANIZATION, orgId, MediaUsageSlot.LOGO, PublicationStatus.PRIVATE, Visibility.ORGANIZATION_PRIVATE, null)
		} returns sampleAssignment(asset.id, visibility = Visibility.ORGANIZATION_PRIVATE, publicationStatus = PublicationStatus.PRIVATE)
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.assignOrganizationMedia(orgId, MediaUsageSlot.LOGO, asset.id, null, currentUser)

		assertEquals(Visibility.ORGANIZATION_PRIVATE, result.visibility)
		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `entity assignment rejects an asset uploaded for another slot`() {
		val target = ResolvedMediaTarget(
			organizationId = orgId,
			entityType = MediaEntityType.PARTICIPANT,
			entityId = UUID.randomUUID(),
			allowedSlots = setOf(MediaUsageSlot.PROFILE_PHOTO),
			visibility = Visibility.HOUSEHOLD_PRIVATE,
		)
		val asset = readyAsset()
		every {
			mediaEntityAccessService.resolveForManage(orgId, target.entityType, target.entityId, currentUser)
		} returns target
		every { mediaEntityAccessService.requireAllowedSlot(target, MediaUsageSlot.PROFILE_PHOTO) } just runs
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset

		assertFailsWith<ValidationException> {
			service.assignEntityMedia(
				orgId,
				target.entityType,
				target.entityId,
				MediaUsageSlot.PROFILE_PHOTO,
				asset.id,
				null,
				currentUser,
			)
		}
	}

	@Test
	fun `listActive requires only active membership, not manager role`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
		every { mediaAssignmentRepository.listActive(MediaEntityType.ORGANIZATION, orgId) } returns emptyList()

		service.listActiveOrganizationMedia(orgId, currentUser)

		verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
		verify(exactly = 0) { membershipService.requireManagerRole(any(), any()) }
	}

	private fun readyAsset() = MediaAsset(
		id = UUID.randomUUID(),
		organizationId = orgId,
		uploadedByUserId = currentUser.userId,
		intendedUsageSlot = MediaUsageSlot.LOGO,
		originalFileName = "logo.png",
		declaredContentType = "image/png",
		detectedContentType = "image/png",
		storageKey = "organizations/$orgId/media/${UUID.randomUUID()}/original.png",
		byteSize = 1024,
		checksumSha256 = "abc123",
		widthPx = 100,
		heightPx = 100,
		status = MediaAssetStatus.READY,
		rejectionReason = null,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun sampleAssignment(
		assetId: UUID,
		visibility: Visibility = Visibility.ORGANIZATION_PRIVATE,
		publicationStatus: PublicationStatus = PublicationStatus.PRIVATE,
	) = MediaAssignment(
		id = UUID.randomUUID(),
		organizationId = orgId,
		assetId = assetId,
		entityType = MediaEntityType.ORGANIZATION,
		entityId = orgId,
		usageSlot = MediaUsageSlot.LOGO,
		publicationStatus = publicationStatus,
		visibility = visibility,
		altText = null,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun publishedOrgPage() = PublicPage(
		id = UUID.randomUUID(),
		organizationId = orgId,
		pageType = PageType.ORGANIZATION,
		entityId = orgId,
		slug = "riverside-soccer",
		title = "Riverside Soccer",
		summary = null,
		status = PageStatus.PUBLISHED,
		publishedAt = Instant.now(),
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun managerMembership() = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = orgId,
		userId = currentUser.userId,
		role = MembershipRole.ADMINISTRATOR,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}

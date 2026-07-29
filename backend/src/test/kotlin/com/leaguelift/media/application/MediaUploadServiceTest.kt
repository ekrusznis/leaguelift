package com.leaguelift.media.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.media.domain.MediaAsset
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.infra.ObjectHead
import com.leaguelift.media.infra.PresignedUpload
import com.leaguelift.media.infra.SpacesClient
import com.leaguelift.media.persistence.MediaAssetRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaUploadServiceTest {

	private val mediaAssetRepository = mockk<MediaAssetRepository>()
	private val spacesClient = mockk<SpacesClient>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = MediaUploadService(mediaAssetRepository, spacesClient, membershipService, auditService, outboxWriter)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	@Test
	fun `requestUpload requires manager role`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.insert(any(), any(), any(), any(), any(), any(), any()) } returns pendingAsset()
		every { spacesClient.presignedPutUrl(any(), any(), any()) } returns PresignedUpload("https://minio.local/put", Instant.now())
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		service.requestUpload(orgId, MediaUsageSlot.LOGO, "logo.png", "image/png", 1024, currentUser)

		verify(exactly = 1) { membershipService.requireManagerRole(orgId, currentUser) }
	}

	@Test
	fun `requestUpload rejects a content type not allowed for the slot`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

		assertFailsWith<ValidationException> {
			service.requestUpload(orgId, MediaUsageSlot.COVER, "logo.svg", "image/svg+xml", 1024, currentUser)
		}
	}

	@Test
	fun `requestUpload rejects a file over the slot's size limit`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

		assertFailsWith<ValidationException> {
			service.requestUpload(orgId, MediaUsageSlot.LOGO, "logo.png", "image/png", 50L * 1024 * 1024, currentUser)
		}
	}

	@Test
	fun `confirmUpload transitions a valid png to READY`() {
		val asset = pendingAsset(declaredContentType = "image/png")
		val readyAsset = asset.copy(status = MediaAssetStatus.READY, detectedContentType = "image/png")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returnsMany listOf(asset, readyAsset)
		every { spacesClient.headObject(asset.storageKey) } returns ObjectHead(exists = true, contentLength = 100)
		every { spacesClient.getObjectBytesCapped(asset.storageKey, any()) } returns pngBytes(10, 10)
		every { mediaAssetRepository.markConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		val result = service.confirmUpload(orgId, asset.id, currentUser)

		assertEquals(MediaAssetStatus.READY, result.asset.status)
		verify(exactly = 1) {
			mediaAssetRepository.markConfirmed(
				asset.id, orgId, MediaAssetStatus.READY, "image/png", any(), any(), 10, 10, null,
			)
		}
		verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "media.confirmed", "media_asset", asset.id, any()) }
		verify(exactly = 1) { outboxWriter.write("media_asset", asset.id, orgId, "media.asset.ready", any()) }
	}

	@Test
	fun `confirmUpload rejects an oversized file`() {
		val asset = pendingAsset(declaredContentType = "image/png")
		val rejectedAsset = asset.copy(status = MediaAssetStatus.REJECTED, rejectionReason = "FILE_TOO_LARGE")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returnsMany listOf(asset, rejectedAsset)
		every { spacesClient.headObject(asset.storageKey) } returns ObjectHead(exists = true, contentLength = 999)
		// One byte over the slot's 10MB logo limit — the capped-read contract returns
		// maxBytes+1 bytes when the real object exceeds the limit.
		every { spacesClient.getObjectBytesCapped(asset.storageKey, any()) } returns ByteArray(10 * 1024 * 1024 + 1)
		every { mediaAssetRepository.markConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.confirmUpload(orgId, asset.id, currentUser)

		assertEquals(MediaAssetStatus.REJECTED, result.asset.status)
		verify(exactly = 1) {
			mediaAssetRepository.markConfirmed(asset.id, orgId, MediaAssetStatus.REJECTED, null, any(), null, null, null, "FILE_TOO_LARGE")
		}
		verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "media.rejected", "media_asset", asset.id, any()) }
		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `confirmUpload rejects a magic-byte mismatch against the declared content type`() {
		val asset = pendingAsset(declaredContentType = "image/png")
		val rejectedAsset = asset.copy(status = MediaAssetStatus.REJECTED, rejectionReason = "CONTENT_TYPE_MISMATCH")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returnsMany listOf(asset, rejectedAsset)
		every { spacesClient.headObject(asset.storageKey) } returns ObjectHead(exists = true, contentLength = 100)
		// Declares PNG but the actual bytes are a JPEG — the defense-in-depth magic-byte
		// sniff must catch this even though the presigned PUT's Content-Type header matched.
		every { spacesClient.getObjectBytesCapped(asset.storageKey, any()) } returns jpegBytes(10, 10)
		every { mediaAssetRepository.markConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.confirmUpload(orgId, asset.id, currentUser)

		assertEquals(MediaAssetStatus.REJECTED, result.asset.status)
		verify(exactly = 1) {
			mediaAssetRepository.markConfirmed(asset.id, orgId, MediaAssetStatus.REJECTED, null, any(), null, null, null, "CONTENT_TYPE_MISMATCH")
		}
	}

	@Test
	fun `confirmUpload rejects bytes with a valid PNG signature but no decodable image data`() {
		val asset = pendingAsset(declaredContentType = "image/png")
		val rejectedAsset = asset.copy(status = MediaAssetStatus.REJECTED, rejectionReason = "INVALID_IMAGE")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returnsMany listOf(asset, rejectedAsset)
		every { spacesClient.headObject(asset.storageKey) } returns ObjectHead(exists = true, contentLength = 8)
		// Just the 8-byte PNG signature, no IHDR/IDAT chunks — passes the magic-byte sniff
		// but ImageIO cannot decode it.
		val truncatedPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
		every { spacesClient.getObjectBytesCapped(asset.storageKey, any()) } returns truncatedPng
		every { mediaAssetRepository.markConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.confirmUpload(orgId, asset.id, currentUser)

		assertEquals(MediaAssetStatus.REJECTED, result.asset.status)
		assertEquals("INVALID_IMAGE", result.asset.rejectionReason)
	}

	@Test
	fun `confirmUpload rejects an image over the dimension cap`() {
		val asset = pendingAsset(declaredContentType = "image/png")
		val rejectedAsset = asset.copy(status = MediaAssetStatus.REJECTED, rejectionReason = "IMAGE_DIMENSIONS_TOO_LARGE")
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returnsMany listOf(asset, rejectedAsset)
		every { spacesClient.headObject(asset.storageKey) } returns ObjectHead(exists = true, contentLength = 1000)
		every { spacesClient.getObjectBytesCapped(asset.storageKey, any()) } returns pngBytes(10_001, 1)
		every { mediaAssetRepository.markConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

		val result = service.confirmUpload(orgId, asset.id, currentUser)

		assertEquals(MediaAssetStatus.REJECTED, result.asset.status)
		assertEquals("IMAGE_DIMENSIONS_TOO_LARGE", result.asset.rejectionReason)
	}

	@Test
	fun `confirmUpload throws NotFoundException for an unknown asset`() {
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(any(), orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.confirmUpload(orgId, UUID.randomUUID(), currentUser)
		}
	}

	@Test
	fun `confirmUpload throws ValidationException when the asset was already confirmed`() {
		val asset = pendingAsset().copy(status = MediaAssetStatus.READY)
		every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
		every { mediaAssetRepository.findById(asset.id, orgId) } returns asset

		assertFailsWith<ValidationException> {
			service.confirmUpload(orgId, asset.id, currentUser)
		}
	}

	private fun pendingAsset(declaredContentType: String = "image/png") = MediaAsset(
		id = UUID.randomUUID(),
		organizationId = orgId,
		uploadedByUserId = currentUser.userId,
		intendedUsageSlot = MediaUsageSlot.LOGO,
		originalFileName = "logo.png",
		declaredContentType = declaredContentType,
		detectedContentType = null,
		storageKey = "organizations/$orgId/media/${UUID.randomUUID()}/original.png",
		byteSize = null,
		checksumSha256 = null,
		widthPx = null,
		heightPx = null,
		status = MediaAssetStatus.PENDING_UPLOAD,
		rejectionReason = null,
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

	private fun pngBytes(width: Int, height: Int): ByteArray {
		val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
		val out = ByteArrayOutputStream()
		ImageIO.write(image, "png", out)
		return out.toByteArray()
	}

	private fun jpegBytes(width: Int, height: Int): ByteArray {
		val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
		val out = ByteArrayOutputStream()
		ImageIO.write(image, "jpg", out)
		return out.toByteArray()
	}
}

package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.Visibility
import com.rally26.media.infra.ObjectHead
import com.rally26.media.infra.PresignedUpload
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.outbox.application.OutboxWriter
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
    private val mediaEntityAccessService = mockk<MediaEntityAccessService>()
    private val auditService = mockk<AuditService>()
    private val outboxWriter = mockk<OutboxWriter>()
    private val service =
        MediaUploadService(mediaAssetRepository, spacesClient, membershipService, mediaEntityAccessService, auditService, outboxWriter)

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
    fun `target-scoped request uses entity authorization instead of organization manager role`() {
        val participantId = UUID.randomUUID()
        val target =
            ResolvedMediaTarget(
                organizationId = orgId,
                entityType = MediaEntityType.PARTICIPANT,
                entityId = participantId,
                allowedSlots = setOf(MediaUsageSlot.PROFILE_PHOTO),
                visibility = Visibility.HOUSEHOLD_PRIVATE,
            )
        every {
            mediaEntityAccessService.resolveForManage(orgId, MediaEntityType.PARTICIPANT, participantId, currentUser)
        } returns target
        every { mediaEntityAccessService.requireAllowedSlot(target, MediaUsageSlot.PROFILE_PHOTO) } just runs
        every { mediaAssetRepository.insert(any(), any(), any(), any(), any(), any(), any()) } returns
            pendingAsset().copy(intendedUsageSlot = MediaUsageSlot.PROFILE_PHOTO)
        every { spacesClient.presignedPutUrl(any(), any(), any()) } returns PresignedUpload("https://minio.local/put", Instant.now())
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        service.requestUpload(
            orgId,
            MediaUsageSlot.PROFILE_PHOTO,
            "profile.webp",
            "image/webp",
            1024,
            currentUser,
            MediaEntityType.PARTICIPANT,
            participantId,
        )

        verify(exactly = 0) { membershipService.requireManagerRole(any(), any()) }
        verify(exactly = 1) {
            mediaEntityAccessService.resolveForManage(orgId, MediaEntityType.PARTICIPANT, participantId, currentUser)
        }
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
                asset.id,
                orgId,
                MediaAssetStatus.READY,
                "image/png",
                any(),
                any(),
                10,
                10,
                null,
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
            mediaAssetRepository.markConfirmed(
                asset.id,
                orgId,
                MediaAssetStatus.REJECTED,
                null,
                any(),
                null,
                null,
                null,
                "CONTENT_TYPE_MISMATCH",
            )
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

    private fun pendingAsset(declaredContentType: String = "image/png") =
        MediaAsset(
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

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun pngBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun jpegBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }
}

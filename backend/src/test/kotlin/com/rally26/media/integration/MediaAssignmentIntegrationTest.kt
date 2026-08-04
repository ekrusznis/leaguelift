package com.rally26.media.integration

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.application.MediaUploadService
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.infra.ObjectHead
import com.rally26.media.infra.PresignedUpload
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/**
 * Exercises re-assigning an organization's logo: the prior assignment must be RETIRED,
 * its asset ARCHIVED, and exactly one active assignment must remain for the slot —
 * exercising the partial unique index added in V9__media.sql. SpacesClient is mocked;
 * this is a DB-behavior test, not a storage-I/O test.
 */
class MediaAssignmentIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var mediaUploadService: MediaUploadService

	@Autowired
	lateinit var mediaAssignmentService: MediaAssignmentService

	@Autowired
	lateinit var mediaAssetRepository: MediaAssetRepository

	@Autowired
	lateinit var mediaAssignmentRepository: MediaAssignmentRepository

	@MockkBean
	lateinit var spacesClient: SpacesClient

	@Test
	fun `re-assigning a slot retires the old assignment and archives the old asset`() {
		every { spacesClient.presignedPutUrl(any(), any(), any()) } returns PresignedUpload("https://minio.local/put", Instant.now())
		every { spacesClient.headObject(any()) } returns ObjectHead(exists = true, contentLength = 100)
		every { spacesClient.getObjectBytesCapped(any(), any()) } returns pngBytes()

		val owner = registerUser()
		val organization = organizationService.create(
			"Riverside Soccer",
			"riverside-soccer-reassign-${System.nanoTime()}",
			OrganizationType.RECREATIONAL_LEAGUE,
			owner,
		)

		val firstUpload = mediaUploadService.requestUpload(organization.id, MediaUsageSlot.LOGO, "logo-1.png", "image/png", 1024, owner)
		mediaUploadService.confirmUpload(organization.id, firstUpload.asset.id, owner)
		mediaAssignmentService.assignOrganizationMedia(organization.id, MediaUsageSlot.LOGO, firstUpload.asset.id, null, owner)

		val secondUpload = mediaUploadService.requestUpload(organization.id, MediaUsageSlot.LOGO, "logo-2.png", "image/png", 1024, owner)
		mediaUploadService.confirmUpload(organization.id, secondUpload.asset.id, owner)
		val secondAssignment = mediaAssignmentService.assignOrganizationMedia(organization.id, MediaUsageSlot.LOGO, secondUpload.asset.id, null, owner)

		val retiredFirstAssignment = mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, organization.id, MediaUsageSlot.LOGO)
		assertEquals(secondAssignment.id, retiredFirstAssignment?.id, "the active assignment must now be the second one")

		val archivedFirstAsset = mediaAssetRepository.findById(firstUpload.asset.id, organization.id)
		assertEquals(MediaAssetStatus.ARCHIVED, archivedFirstAsset?.status)

		val activeAssignments = mediaAssignmentRepository.listActive(MediaEntityType.ORGANIZATION, organization.id)
			.filter { it.usageSlot == MediaUsageSlot.LOGO }
		assertEquals(1, activeAssignments.size, "exactly one active assignment must exist for (ORGANIZATION, org, LOGO)")
		assertEquals(secondAssignment.assetId, activeAssignments.single().assetId)
	}

	private fun registerUser(): CurrentUser {
		val appUser = passwordAuthenticationService.register("media-reassign-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}

	private fun pngBytes(): ByteArray {
		val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
		val out = ByteArrayOutputStream()
		ImageIO.write(image, "png", out)
		return out.toByteArray()
	}
}

package com.leaguelift.media.integration

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaUploadService
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.infra.ObjectHead
import com.leaguelift.media.infra.PresignedUpload
import com.leaguelift.media.infra.SpacesClient
import com.leaguelift.organization.application.OrganizationService
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.testsupport.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.assertFailsWith

/**
 * Exercises DESIGN-DOC.md section 22.3 critical scenario 1 ("a user from Organization A
 * cannot read Organization B") for the media module: a manager of one organization must
 * never be able to upload, confirm, assign, or list media for an organization they are
 * not a member of.
 *
 * SpacesClient is mocked (@MockkBean) — this test exercises DB/authorization behavior,
 * not real storage I/O (see MediaUploadEndToEndIntegrationTest for the real-MinIO path).
 * Requires Docker (Testcontainers, via AbstractIntegrationTest).
 */
class MediaOrganizationIsolationIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var mediaUploadService: MediaUploadService

	@Autowired
	lateinit var mediaAssignmentService: MediaAssignmentService

	@MockkBean
	lateinit var spacesClient: SpacesClient

	@Test
	fun `an outsider cannot upload, confirm, assign, or list an organization's media`() {
		every { spacesClient.presignedPutUrl(any(), any(), any()) } returns PresignedUpload("https://minio.local/put", Instant.now())
		every { spacesClient.headObject(any()) } returns ObjectHead(exists = true, contentLength = 100)
		every { spacesClient.getObjectBytesCapped(any(), any()) } returns pngBytes()

		val owner = registerUser("media-owner")
		val organization = organizationService.create(
			"Riverside Soccer",
			"riverside-soccer-media-${System.nanoTime()}",
			OrganizationType.RECREATIONAL_LEAGUE,
			owner,
		)
		val outsider = registerUser("media-outsider")

		val requested = mediaUploadService.requestUpload(
			organization.id, MediaUsageSlot.LOGO, "logo.png", "image/png", 1024, owner,
		)
		mediaUploadService.confirmUpload(organization.id, requested.asset.id, owner)
		val assignment = mediaAssignmentService.assignOrganizationMedia(organization.id, MediaUsageSlot.LOGO, requested.asset.id, null, owner)

		// The owner can see their own organization's assignment.
		assert(mediaAssignmentService.listActiveOrganizationMedia(organization.id, owner).any { it.id == assignment.id })

		assertFailsWith<ForbiddenException> {
			mediaUploadService.requestUpload(organization.id, MediaUsageSlot.LOGO, "evil.png", "image/png", 1024, outsider)
		}
		assertFailsWith<ForbiddenException> {
			mediaUploadService.confirmUpload(organization.id, requested.asset.id, outsider)
		}
		assertFailsWith<ForbiddenException> {
			mediaAssignmentService.assignOrganizationMedia(organization.id, MediaUsageSlot.COVER, requested.asset.id, null, outsider)
		}
		assertFailsWith<ForbiddenException> {
			mediaAssignmentService.listActiveOrganizationMedia(organization.id, outsider)
		}
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}

	private fun pngBytes(): ByteArray {
		val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
		val out = ByteArrayOutputStream()
		ImageIO.write(image, "png", out)
		return out.toByteArray()
	}
}

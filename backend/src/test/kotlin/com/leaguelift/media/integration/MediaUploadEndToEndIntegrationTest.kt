package com.leaguelift.media.integration

import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.media.application.MediaAssignmentService
import com.leaguelift.media.application.MediaUploadService
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.organization.application.OrganizationService
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEST_BUCKET = "leaguelift-media-e2e-test"

/**
 * The one test exercising the real S3-compatible storage path end-to-end (request
 * upload -> real HTTP PUT to the presigned URL -> confirm -> assign) against a real
 * MinIOContainer, rather than the mocked SpacesClient used by
 * MediaOrganizationIsolationIntegrationTest/MediaAssignmentIntegrationTest — catches
 * presigner/path-style/serialization bugs those tests can't. Kept as its own test
 * class/commit since it's the first non-Postgres Testcontainer in this repo (easy to
 * isolate/revert if it proves flaky in CI).
 *
 * Uses the same singleton-container pattern as AbstractIntegrationTest.postgres —
 * started once via `.also { it.start() }`, not per-class.
 */
class MediaUploadEndToEndIntegrationTest : AbstractIntegrationTest() {

	companion object {
		@JvmStatic
		val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-11-07T00-52-20Z")
			.withUserName("leaguelift-test")
			.withPassword("leaguelift-test-secret")
			.also {
				it.start()
				createBucket(it)
			}

		private fun createBucket(container: MinIOContainer) {
			val client = S3Client.builder()
				.endpointOverride(URI.create(container.s3URL))
				.region(Region.US_EAST_1)
				.credentialsProvider(
					StaticCredentialsProvider.create(AwsBasicCredentials.create(container.userName, container.password)),
				)
				.forcePathStyle(true)
				.build()
			client.use { it.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build()) }
		}

		@DynamicPropertySource
		@JvmStatic
		fun registerSpacesProperties(registry: DynamicPropertyRegistry) {
			registry.add("leaguelift.spaces.endpoint") { minio.s3URL }
			registry.add("leaguelift.spaces.access-key") { minio.userName }
			registry.add("leaguelift.spaces.secret-key") { minio.password }
			registry.add("leaguelift.spaces.bucket") { TEST_BUCKET }
			registry.add("leaguelift.spaces.region") { "us-east-1" }
		}
	}

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var mediaUploadService: MediaUploadService

	@Autowired
	lateinit var mediaAssignmentService: MediaAssignmentService

	@Test
	fun `upload confirm and assign a logo through real S3-compatible storage`() {
		val appUser = passwordAuthenticationService.register("media-e2e-${System.nanoTime()}@example.com", "password1234", "Test User")
		val owner = passwordAuthenticationService.toCurrentUser(appUser)
		val organization = organizationService.create(
			"Riverside Soccer",
			"riverside-soccer-e2e-${System.nanoTime()}",
			OrganizationType.RECREATIONAL_LEAGUE,
			owner,
		)

		val bytes = pngBytes()
		val requested = mediaUploadService.requestUpload(organization.id, MediaUsageSlot.LOGO, "logo.png", "image/png", bytes.size.toLong(), owner)

		// The browser never proxies bytes through the Spring API — PUT directly to the
		// presigned URL, mirroring what uploadToSignedUrl.ts does client-side.
		val httpClient = HttpClient.newHttpClient()
		val putRequest = HttpRequest.newBuilder(URI.create(requested.uploadUrl))
			.header("Content-Type", "image/png")
			.PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
			.build()
		val putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.discarding())
		assertEquals(200, putResponse.statusCode())

		val confirmed = mediaUploadService.confirmUpload(organization.id, requested.asset.id, owner)
		assertEquals(MediaAssetStatus.READY, confirmed.asset.status)
		assertEquals(10, confirmed.asset.widthPx)
		assertEquals(10, confirmed.asset.heightPx)

		val assignment = mediaAssignmentService.assign(organization.id, MediaUsageSlot.LOGO, requested.asset.id, "Riverside Soccer logo", owner)
		val active = mediaAssignmentService.listActive(organization.id, owner)
		assertTrue(active.any { it.id == assignment.id })
	}

	private fun pngBytes(): ByteArray {
		val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
		val out = ByteArrayOutputStream()
		ImageIO.write(image, "png", out)
		return out.toByteArray()
	}
}

package com.rally26.store.application

import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.order.domain.PersonalizationPlacement
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwagDesignCompositorTest {
    private val mediaAssetRepository = mockk<MediaAssetRepository>()
    private val spacesClient = mockk<SpacesClient>()
    private val compositor = SwagDesignCompositor(mediaAssetRepository, spacesClient)

    private val orgId = UUID.randomUUID()
    private val logoAssetId = UUID.randomUUID()

    private fun realLogoPngBytes(): ByteArray {
        val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillOval(20, 20, 160, 160)
        g.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun logoAsset() =
        MediaAsset(
            id = logoAssetId,
            organizationId = orgId,
            uploadedByUserId = UUID.randomUUID(),
            intendedUsageSlot = MediaUsageSlot.LOGO,
            originalFileName = "team-logo.png",
            declaredContentType = "image/png",
            detectedContentType = "image/png",
            storageKey = "organizations/$orgId/media/$logoAssetId/original.png",
            byteSize = 1024L,
            checksumSha256 = "checksum",
            widthPx = 200,
            heightPx = 200,
            status = MediaAssetStatus.READY,
            rejectionReason = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `compose produces a print-ready PNG sized to the real print area and uploads it`() {
        val orderId = UUID.randomUUID()
        val orderItemId = UUID.randomUUID()
        every { mediaAssetRepository.findById(logoAssetId, orgId) } returns logoAsset()
        every { spacesClient.getObjectBytesCapped(logoAsset().storageKey, any()) } returns realLogoPngBytes()
        val uploadedBytes = slot<ByteArray>()
        every { spacesClient.putObject(any(), "image/png", capture(uploadedBytes)) } returns Unit
        every { spacesClient.presignedGetUrl(any(), any()) } returns "https://signed.example.com/composited.png"

        val url =
            compositor.compose(
                organizationId = orgId,
                orderId = orderId,
                orderItemId = orderItemId,
                swagLogoMediaAssetId = logoAssetId,
                printAreaWidthPx = 3909,
                printAreaHeightPx = 4431,
                personalizationName = "Johnson",
                personalizationNumber = "7",
                personalizationPlacement = PersonalizationPlacement.BACK,
            )

        assertEquals("https://signed.example.com/composited.png", url)
        verify(exactly = 1) { spacesClient.putObject("swag-shop/composited/$orderId/$orderItemId.png", "image/png", any()) }

        val composited = ImageIO.read(ByteArrayInputStream(uploadedBytes.captured))
        assertEquals(3909, composited.width)
        assertEquals(4431, composited.height)
        // Not fully transparent -- the logo/text were actually drawn onto the canvas.
        val hasOpaquePixel = (0 until composited.width step 97).any { x -> (0 until composited.height step 97).any { y -> (composited.getRGB(x, y) ushr 24) != 0 } }
        assertTrue(hasOpaquePixel, "composited image should not be fully transparent")
    }

    @Test
    fun `compose without personalization still draws the logo`() {
        every { mediaAssetRepository.findById(logoAssetId, orgId) } returns logoAsset()
        every { spacesClient.getObjectBytesCapped(logoAsset().storageKey, any()) } returns realLogoPngBytes()
        val uploadedBytes = slot<ByteArray>()
        every { spacesClient.putObject(any(), "image/png", capture(uploadedBytes)) } returns Unit
        every { spacesClient.presignedGetUrl(any(), any()) } returns "https://signed.example.com/composited.png"

        compositor.compose(
            organizationId = orgId,
            orderId = UUID.randomUUID(),
            orderItemId = UUID.randomUUID(),
            swagLogoMediaAssetId = logoAssetId,
            printAreaWidthPx = 1000,
            printAreaHeightPx = 1000,
            personalizationName = null,
            personalizationNumber = null,
            personalizationPlacement = null,
        )

        val composited = ImageIO.read(ByteArrayInputStream(uploadedBytes.captured))
        assertEquals(1000, composited.width)
        assertEquals(1000, composited.height)
    }
}

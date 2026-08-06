package com.rally26.store.application

import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.order.domain.PersonalizationPlacement
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertNull
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

    private fun stubUploads(): MutableList<ByteArray> {
        every { mediaAssetRepository.findById(logoAssetId, orgId) } returns logoAsset()
        every { spacesClient.getObjectBytesCapped(logoAsset().storageKey, any()) } returns realLogoPngBytes()
        val uploaded = mutableListOf<ByteArray>()
        every { spacesClient.putObject(any(), "image/png", capture(uploaded)) } returns Unit
        every { spacesClient.presignedGetUrl(any(), any()) } answers { "https://signed.example.com/${firstArg<String>()}" }
        return uploaded
    }

    @Test
    fun `BACK placement composites two distinct print files, front logo-only and back personalization-only`() {
        val orderId = UUID.randomUUID()
        val orderItemId = UUID.randomUUID()
        val uploaded = stubUploads()

        val result =
            compositor.compose(
                organizationId = orgId,
                orderId = orderId,
                orderItemId = orderItemId,
                swagLogoMediaAssetId = logoAssetId,
                printAreaWidthPx = 3909,
                printAreaHeightPx = 4431,
                backPrintAreaWidthPx = 3000,
                backPrintAreaHeightPx = 3600,
                personalizationName = "Johnson",
                personalizationNumber = "7",
                personalizationPlacement = PersonalizationPlacement.BACK,
            )

        assertEquals("https://signed.example.com/swag-shop/composited/$orderId/$orderItemId-front.png", result.frontUrl)
        assertEquals("https://signed.example.com/swag-shop/composited/$orderId/$orderItemId-back.png", result.backUrl)
        verify(exactly = 1) { spacesClient.putObject("swag-shop/composited/$orderId/$orderItemId-front.png", "image/png", any()) }
        verify(exactly = 1) { spacesClient.putObject("swag-shop/composited/$orderId/$orderItemId-back.png", "image/png", any()) }
        assertEquals(2, uploaded.size)

        val front = ImageIO.read(ByteArrayInputStream(uploaded[0]))
        assertEquals(3909, front.width)
        assertEquals(4431, front.height)
        val back = ImageIO.read(ByteArrayInputStream(uploaded[1]))
        assertEquals(3000, back.width)
        assertEquals(3600, back.height)
        assertTrue(hasOpaquePixel(front), "front canvas should have the logo drawn on it")
        assertTrue(hasOpaquePixel(back), "back canvas should have the name/number drawn on it")
    }

    @Test
    fun `LEFT_CHEST placement composites a single front print file, no back`() {
        val uploaded = stubUploads()

        val result =
            compositor.compose(
                organizationId = orgId,
                orderId = UUID.randomUUID(),
                orderItemId = UUID.randomUUID(),
                swagLogoMediaAssetId = logoAssetId,
                printAreaWidthPx = 3909,
                printAreaHeightPx = 4431,
                backPrintAreaWidthPx = null,
                backPrintAreaHeightPx = null,
                personalizationName = "Johnson",
                personalizationNumber = "7",
                personalizationPlacement = PersonalizationPlacement.LEFT_CHEST,
            )

        assertNull(result.backUrl)
        assertEquals(1, uploaded.size)
        verify(exactly = 0) { spacesClient.putObject(match { it.contains("-back") }, any(), any()) }
    }

    @Test
    fun `compose without personalization still draws the logo, front only`() {
        val uploaded = stubUploads()

        val result =
            compositor.compose(
                organizationId = orgId,
                orderId = UUID.randomUUID(),
                orderItemId = UUID.randomUUID(),
                swagLogoMediaAssetId = logoAssetId,
                printAreaWidthPx = 1000,
                printAreaHeightPx = 1000,
                backPrintAreaWidthPx = null,
                backPrintAreaHeightPx = null,
                personalizationName = null,
                personalizationNumber = null,
                personalizationPlacement = null,
            )

        assertNull(result.backUrl)
        val composited = ImageIO.read(ByteArrayInputStream(uploaded.single()))
        assertEquals(1000, composited.width)
        assertEquals(1000, composited.height)
        assertTrue(hasOpaquePixel(composited), "composited image should not be fully transparent")
    }

    private fun hasOpaquePixel(image: BufferedImage): Boolean =
        (0 until image.width step 37).any { x -> (0 until image.height step 37).any { y -> (image.getRGB(x, y) ushr 24) != 0 } }
}

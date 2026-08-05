package com.rally26.store.application

import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.order.domain.PersonalizationPlacement
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO

private const val MAX_LOGO_BYTES = 10L * 1024 * 1024

/**
 * Swag Shop Path 1/Quick compositing (DESIGN-DOC.md section 13). Pure JDK
 * (java.awt/ImageIO) — no new dependency, matching this codebase's established
 * "plain implementation over new dependency" precedent (ADR-028's hand-rolled
 * ICS, ADR-032's hand-rolled CSV parser). Runs once, after payment confirms,
 * from OrderService.createInitialFulfillment — never pre-checkout, so a buyer
 * never waits on image generation.
 *
 * Produces a transparent-background, print-ready PNG sized to the variant's
 * real Printify print-area dimensions: the team logo in the fixed zone staff
 * configured at Swag Shop setup, plus optional name/number text in a curated
 * placement preset. Not a garment mockup — Printify's own print production
 * applies this onto the actual apparel, the same as today's static per-product
 * design image already does.
 */
@Component
class SwagDesignCompositor(
    private val mediaAssetRepository: MediaAssetRepository,
    private val spacesClient: SpacesClient,
) {
    fun compose(
        organizationId: UUID,
        orderId: UUID,
        orderItemId: UUID,
        swagLogoMediaAssetId: UUID,
        printAreaWidthPx: Int,
        printAreaHeightPx: Int,
        personalizationName: String?,
        personalizationNumber: String?,
        personalizationPlacement: PersonalizationPlacement?,
    ): String {
        val logoAsset =
            mediaAssetRepository.findById(swagLogoMediaAssetId, organizationId)
                ?: error("Swag Shop logo asset $swagLogoMediaAssetId could not be found")
        val logoBytes = spacesClient.getObjectBytesCapped(logoAsset.storageKey, MAX_LOGO_BYTES)
        val logoImage =
            ImageIO.read(ByteArrayInputStream(logoBytes))
                ?: error("Swag Shop logo asset $swagLogoMediaAssetId could not be decoded as a raster image")

        val canvas = BufferedImage(printAreaWidthPx, printAreaHeightPx, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawLogoAndPersonalization(g, canvas, logoImage, personalizationName, personalizationNumber, personalizationPlacement)
        } finally {
            g.dispose()
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(canvas, "png", output)
        val key = "swag-shop/composited/$orderId/$orderItemId.png"
        spacesClient.putObject(key, "image/png", output.toByteArray())
        // Same TTL already used for the existing static-design flow (MediaReadService.describe) —
        // Printify fetches this URL near-immediately as part of order submission, no new expiry risk.
        return spacesClient.presignedGetUrl(key, Duration.ofMinutes(15))
    }

    /**
     * Fixed, curated layouts for Path 1 — the buyer never repositions anything.
     * LEFT_CHEST/RIGHT_CHEST: a small corner logo with name/number stacked beneath it,
     * mirroring standard jersey left/right-chest placement. BACK/no placement: a larger
     * centered logo with a name arc above a large centered number, standard jersey-back
     * layout. Deliberately simple — real per-org placement-zone configuration is a
     * Path 2 ("Custom") concern, not this slice.
     */
    private fun drawLogoAndPersonalization(
        g: java.awt.Graphics2D,
        canvas: BufferedImage,
        logoImage: BufferedImage,
        name: String?,
        number: String?,
        placement: PersonalizationPlacement?,
    ) {
        val w = canvas.width
        val h = canvas.height
        when (placement) {
            PersonalizationPlacement.LEFT_CHEST, PersonalizationPlacement.RIGHT_CHEST -> {
                val logoSize = (w * 0.28).toInt()
                val x = if (placement == PersonalizationPlacement.LEFT_CHEST) (w * 0.08).toInt() else (w * 0.64).toInt()
                val y = (h * 0.08).toInt()
                drawScaledLogo(g, logoImage, x, y, logoSize, logoSize)
                var textY = y + logoSize + (h * 0.04).toInt()
                if (!name.isNullOrBlank()) {
                    textY = drawCenteredText(g, name.uppercase(), x + logoSize / 2, textY, (h * 0.035).toInt().coerceAtLeast(12))
                }
                if (!number.isNullOrBlank()) {
                    drawCenteredText(g, number, x + logoSize / 2, textY + (h * 0.02).toInt(), (h * 0.05).toInt().coerceAtLeast(16))
                }
            }
            else -> {
                val logoSize = (w * 0.35).toInt()
                val x = (w - logoSize) / 2
                val y = (h * 0.06).toInt()
                drawScaledLogo(g, logoImage, x, y, logoSize, logoSize)
                var textY = y + logoSize + (h * 0.06).toInt()
                if (!name.isNullOrBlank()) {
                    textY = drawCenteredText(g, name.uppercase(), w / 2, textY, (h * 0.06).toInt().coerceAtLeast(18))
                }
                if (!number.isNullOrBlank()) {
                    drawCenteredText(g, number, w / 2, textY + (h * 0.03).toInt(), (h * 0.12).toInt().coerceAtLeast(28))
                }
            }
        }
    }

    private fun drawScaledLogo(
        g: java.awt.Graphics2D,
        logoImage: BufferedImage,
        x: Int,
        y: Int,
        maxWidth: Int,
        maxHeight: Int,
    ) {
        val scale = minOf(maxWidth.toDouble() / logoImage.width, maxHeight.toDouble() / logoImage.height)
        val drawWidth = (logoImage.width * scale).toInt()
        val drawHeight = (logoImage.height * scale).toInt()
        val drawX = x + (maxWidth - drawWidth) / 2
        val drawY = y + (maxHeight - drawHeight) / 2
        g.drawImage(logoImage, drawX, drawY, drawWidth, drawHeight, null)
    }

    /** Returns the y-coordinate just below the drawn text, for stacking a second line beneath it. */
    private fun drawCenteredText(
        g: java.awt.Graphics2D,
        text: String,
        centerX: Int,
        topY: Int,
        fontSizePx: Int,
    ): Int {
        g.font = Font("SansSerif", Font.BOLD, fontSizePx)
        g.color = Color.BLACK
        val metrics = g.fontMetrics
        val textWidth = metrics.stringWidth(text)
        g.drawString(text, centerX - textWidth / 2, topY + metrics.ascent)
        return topY + metrics.height
    }
}

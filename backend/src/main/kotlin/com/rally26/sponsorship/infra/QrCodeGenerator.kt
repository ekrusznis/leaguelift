package com.rally26.sponsorship.infra

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Thin seam over the ZXing library (Phase 6 remainder, ADR-019) — the first real QR
 * implementation in this codebase (DESIGN-DOC.md section 8.3's `qr_code_reference` was
 * design-target only until now). Deliberately dumb: it encodes whatever text it's
 * given as a QR code and returns a `data:` URI the frontend can drop straight into an
 * `<img src>` with no extra fetch/blob plumbing. No click-through tracking, no
 * persistence — see `SponsorshipPackageService.buildShareLink`'s doc for why.
 */
@Component
class QrCodeGenerator {

	fun generatePngDataUri(text: String, sizePx: Int = 320): String {
		val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
		val image = MatrixToImageWriter.toBufferedImage(bitMatrix)
		val output = ByteArrayOutputStream()
		javax.imageio.ImageIO.write(image, "png", output)
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
	}
}

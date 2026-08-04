package com.rally26.sponsorship.infra

import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrCodeGeneratorTest {

	private val generator = QrCodeGenerator()

	@Test
	fun `generatePngDataUri produces a decodable PNG data uri`() {
		val dataUri = generator.generatePngDataUri("https://app.local/sponsors/riverside-fc")

		assertTrue(dataUri.startsWith("data:image/png;base64,"))
		val base64 = dataUri.removePrefix("data:image/png;base64,")
		val bytes = Base64.getDecoder().decode(base64)
		val image = ImageIO.read(bytes.inputStream())
		assertEquals(320, image.width)
		assertEquals(320, image.height)
	}

	@Test
	fun `different input text produces different image bytes`() {
		val a = generator.generatePngDataUri("https://app.local/sponsors/org-a")
		val b = generator.generatePngDataUri("https://app.local/sponsors/org-b")

		assertTrue(a != b)
	}
}

package com.rally26.media.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadLimitsTest {

	private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
	private val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x00)
	private val webpSignature = "RIFF....WEBPVP8 ".toByteArray(Charsets.US_ASCII)
	private val svgBytes = """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"></svg>""".toByteArray()
	private val exeSignature = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x90.toByte(), 0x00)
	private val pdfSignature = "%PDF-1.7\n".toByteArray(Charsets.US_ASCII)

	@Test
	fun `detects PNG by magic bytes`() {
		assertEquals("image/png", UploadLimits.detectContentType(pngSignature))
	}

	@Test
	fun `detects JPEG by magic bytes`() {
		assertEquals("image/jpeg", UploadLimits.detectContentType(jpegSignature))
	}

	@Test
	fun `detects WEBP by RIFF WEBP markers`() {
		assertEquals("image/webp", UploadLimits.detectContentType(webpSignature))
	}

	@Test
	fun `detects SVG by top-level svg tag`() {
		assertEquals("image/svg+xml", UploadLimits.detectContentType(svgBytes))
	}

	@Test
	fun `detects PDF by magic bytes`() {
		assertEquals("application/pdf", UploadLimits.detectContentType(pdfSignature))
	}

	@Test
	fun `returns null for an executable header`() {
		assertNull(UploadLimits.detectContentType(exeSignature))
	}

	@Test
	fun `returns null for plain text that is not svg`() {
		assertNull(UploadLimits.detectContentType("just some text".toByteArray()))
	}

	@Test
	fun `returns null for empty bytes`() {
		assertNull(UploadLimits.detectContentType(ByteArray(0)))
	}

	@Test
	fun `logo allows svg, cover does not`() {
		assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.LOGO, "image/svg+xml"))
		assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.COVER, "image/svg+xml"))
	}

	@Test
	fun `logo and cover both allow png jpeg webp`() {
		for (slot in listOf(MediaUsageSlot.LOGO, MediaUsageSlot.COVER)) {
			for (contentType in listOf("image/png", "image/jpeg", "image/webp")) {
				assertTrue(UploadLimits.isContentTypeAllowed(slot, contentType), "$slot should allow $contentType")
			}
		}
	}

	@Test
	fun `document allows pdf only`() {
		assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "application/pdf"))
		assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "image/png"))
	}

	@Test
	fun `unsupported content type is rejected for both slots`() {
		assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.LOGO, "application/pdf"))
		assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.COVER, "application/pdf"))
	}

	@Test
	fun `svg logo has a smaller size limit than raster logo`() {
		val svgLimit = UploadLimits.maxBytes(MediaUsageSlot.LOGO, "image/svg+xml")
		val rasterLimit = UploadLimits.maxBytes(MediaUsageSlot.LOGO, "image/png")
		assertTrue(svgLimit < rasterLimit)
	}

	@Test
	fun `cover size limit is larger than logo raster limit`() {
		val coverLimit = UploadLimits.maxBytes(MediaUsageSlot.COVER, "image/png")
		val logoLimit = UploadLimits.maxBytes(MediaUsageSlot.LOGO, "image/png")
		assertTrue(coverLimit > logoLimit)
	}
}

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
    fun `document allows pdf and photographed-form image types, not webp or svg`() {
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "application/pdf"))
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "image/png"))
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "image/jpeg"))
        assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "image/webp"))
        assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "image/svg+xml"))
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

    private fun isoBaseMediaBytes(majorBrand: String): ByteArray {
        val header = ByteArray(20)
        // bytes 0-3: box size (irrelevant for detection), bytes 4-7: "ftyp", bytes 8-11: major brand.
        "ftyp".toByteArray(Charsets.US_ASCII).copyInto(header, 4)
        majorBrand.toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        return header
    }

    @Test
    fun `detects MP4 by its ftyp box with a non-QuickTime major brand`() {
        assertEquals("video/mp4", UploadLimits.detectContentType(isoBaseMediaBytes("isom")))
        assertEquals("video/mp4", UploadLimits.detectContentType(isoBaseMediaBytes("mp42")))
    }

    @Test
    fun `detects QuickTime MOV by its ftyp box with the qt major brand`() {
        assertEquals("video/quicktime", UploadLimits.detectContentType(isoBaseMediaBytes("qt  ")))
    }

    @Test
    fun `returns null for bytes too short to contain a ftyp box`() {
        assertNull(UploadLimits.detectContentType(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `household media allows images and video, other slots do not allow video`() {
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.HOUSEHOLD_MEDIA, "image/png"))
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.HOUSEHOLD_MEDIA, "video/mp4"))
        assertTrue(UploadLimits.isContentTypeAllowed(MediaUsageSlot.HOUSEHOLD_MEDIA, "video/quicktime"))
        assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.DOCUMENT, "video/mp4"))
        assertFalse(UploadLimits.isContentTypeAllowed(MediaUsageSlot.COVER, "video/mp4"))
    }

    @Test
    fun `household media video size limit is larger than its own image limit`() {
        val videoLimit = UploadLimits.maxBytes(MediaUsageSlot.HOUSEHOLD_MEDIA, "video/mp4")
        val imageLimit = UploadLimits.maxBytes(MediaUsageSlot.HOUSEHOLD_MEDIA, "image/png")
        assertTrue(videoLimit > imageLimit)
    }

    @Test
    fun `household media video content types are treated as non-image (skip dimension decode)`() {
        assertTrue(UploadLimits.isNonImageContentType("video/mp4"))
        assertTrue(UploadLimits.isNonImageContentType("video/quicktime"))
        assertFalse(UploadLimits.isNonImageContentType("image/png"))
    }
}

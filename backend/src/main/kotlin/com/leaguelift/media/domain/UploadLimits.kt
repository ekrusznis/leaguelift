package com.leaguelift.media.domain

/**
 * Pure validation rules (DESIGN-DOC.md section 11.3) — no repository/S3 dependency, so
 * these are directly unit-testable. [detectContentType] is the defense-in-depth check:
 * a presigned PUT's Content-Type header binding only stops a *header* mismatch, not a
 * bytes/header mismatch, so [MediaUploadService.confirmUpload] must re-derive the real
 * type from the uploaded bytes rather than trusting what the client declared.
 */
object UploadLimits {
	private const val MAX_LOGO_RASTER_BYTES = 10L * 1024 * 1024
	private const val MAX_LOGO_SVG_BYTES = 2L * 1024 * 1024
	private const val MAX_COVER_BYTES = 15L * 1024 * 1024
	private const val MAX_PROFILE_PHOTO_BYTES = 5L * 1024 * 1024
	private const val MAX_PRODUCT_DESIGN_RASTER_BYTES = 15L * 1024 * 1024
	private const val MAX_PRODUCT_DESIGN_SVG_BYTES = 2L * 1024 * 1024
	private const val MAX_DOCUMENT_BYTES = 15L * 1024 * 1024

	const val MAX_DIMENSION_PX = 10_000

	private val LOGO_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/svg+xml")
	private val COVER_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/webp")
	private val PROFILE_PHOTO_CONTENT_TYPES = COVER_CONTENT_TYPES
	// Same shape as LOGO — a product design is typically a team/org graphic being
	// print-placed, so vector (SVG) is just as relevant here as it is for a logo.
	private val PRODUCT_DESIGN_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/svg+xml")
	// A sponsor logo is functionally the same kind of asset as an organization logo
	// (small raster/vector mark), so it reuses LOGO's exact limits rather than defining
	// a new set of numbers.
	private val SPONSOR_LOGO_CONTENT_TYPES = LOGO_CONTENT_TYPES
	// PDF only for this slice — waivers/forms/uploads (DESIGN-DOC.md section 13).
	// Word/Office formats are zip-based and need a different magic-byte check; not
	// worth the extra surface until a real need for them shows up.
	private val DOCUMENT_CONTENT_TYPES = setOf("application/pdf")

	fun allowedContentTypes(slot: MediaUsageSlot): Set<String> = when (slot) {
		MediaUsageSlot.LOGO -> LOGO_CONTENT_TYPES
		MediaUsageSlot.COVER -> COVER_CONTENT_TYPES
		MediaUsageSlot.PROFILE_PHOTO -> PROFILE_PHOTO_CONTENT_TYPES
		MediaUsageSlot.PRODUCT_DESIGN -> PRODUCT_DESIGN_CONTENT_TYPES
		MediaUsageSlot.SPONSOR_LOGO -> SPONSOR_LOGO_CONTENT_TYPES
		MediaUsageSlot.DOCUMENT -> DOCUMENT_CONTENT_TYPES
	}

	fun isContentTypeAllowed(slot: MediaUsageSlot, contentType: String): Boolean =
		contentType in allowedContentTypes(slot)

	fun maxBytes(slot: MediaUsageSlot, contentType: String): Long = when (slot) {
		MediaUsageSlot.LOGO -> if (contentType == "image/svg+xml") MAX_LOGO_SVG_BYTES else MAX_LOGO_RASTER_BYTES
		MediaUsageSlot.COVER -> MAX_COVER_BYTES
		MediaUsageSlot.PROFILE_PHOTO -> MAX_PROFILE_PHOTO_BYTES
		MediaUsageSlot.PRODUCT_DESIGN -> if (contentType == "image/svg+xml") MAX_PRODUCT_DESIGN_SVG_BYTES else MAX_PRODUCT_DESIGN_RASTER_BYTES
		MediaUsageSlot.SPONSOR_LOGO -> if (contentType == "image/svg+xml") MAX_LOGO_SVG_BYTES else MAX_LOGO_RASTER_BYTES
		MediaUsageSlot.DOCUMENT -> MAX_DOCUMENT_BYTES
	}

	/** True for any content type this slot allows that isn't a decodable raster/vector image — e.g. a DOCUMENT slot's PDF. Callers use this to skip image-specific validation (dimension decode/cap) that doesn't apply. */
	fun isNonImageContentType(contentType: String): Boolean = contentType == "application/pdf"

	/**
	 * Sniffs magic bytes to determine the actual image format, independent of the
	 * client-declared content type. Returns null if the bytes don't match any
	 * recognized format — callers should reject rather than trust the declared type.
	 */
	fun detectContentType(bytes: ByteArray): String? {
		if (isPng(bytes)) return "image/png"
		if (isJpeg(bytes)) return "image/jpeg"
		if (isWebp(bytes)) return "image/webp"
		if (isSvg(bytes)) return "image/svg+xml"
		if (isPdf(bytes)) return "application/pdf"
		return null
	}

	private fun isPdf(bytes: ByteArray): Boolean {
		val signature = "%PDF-".toByteArray(Charsets.US_ASCII)
		return bytes.size >= signature.size && bytes.copyOf(signature.size).contentEquals(signature)
	}

	private fun isPng(bytes: ByteArray): Boolean {
		val signature = byteArrayOf(
			0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
		)
		return bytes.size >= signature.size && bytes.copyOf(signature.size).contentEquals(signature)
	}

	private fun isJpeg(bytes: ByteArray): Boolean =
		bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

	private fun isWebp(bytes: ByteArray): Boolean {
		if (bytes.size < 12) return false
		val riff = String(bytes, 0, 4, Charsets.US_ASCII)
		val webp = String(bytes, 8, 4, Charsets.US_ASCII)
		return riff == "RIFF" && webp == "WEBP"
	}

	/**
	 * A top-level `<svg` tag within the first 512 bytes is enough signal for this
	 * slice's purposes — deep SVG sanitization is explicitly deferred (ADR-012).
	 */
	private fun isSvg(bytes: ByteArray): Boolean {
		val headLength = minOf(bytes.size, 512)
		if (headLength == 0) return false
		val head = String(bytes, 0, headLength, Charsets.US_ASCII).trimStart(' ', '\n', '\r', '\t')
		if (!(head.startsWith("<?xml", ignoreCase = true) || head.startsWith("<svg", ignoreCase = true))) return false
		return head.contains("<svg", ignoreCase = true)
	}
}

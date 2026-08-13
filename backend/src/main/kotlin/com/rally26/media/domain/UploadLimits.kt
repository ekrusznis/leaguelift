package com.rally26.media.domain

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

    // Household media (Track 5, 2026-08-13) — guardian-uploaded photos/videos, e.g. a
    // phone-recorded game clip. Materially larger than every other slot's cap since
    // video files are large by nature; synchronous full-byte-read confirmUpload is
    // fine at this size and avoids building async/chunked upload infrastructure this
    // codebase doesn't have anywhere yet — revisit only if real usage shows it doesn't scale.
    private const val MAX_HOUSEHOLD_MEDIA_IMAGE_BYTES = 15L * 1024 * 1024
    private const val MAX_HOUSEHOLD_MEDIA_VIDEO_BYTES = 250L * 1024 * 1024

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

    // PDF plus PNG/JPEG (Phase 37.9, ADR-118) — waivers/forms/uploads (DESIGN-DOC.md
    // section 13). PNG/JPEG were added specifically for mobile's native camera/photo-
    // library upload: a guardian is far more likely to photograph a paper form (a
    // physical exam, a birth certificate) than to already have it saved as a PDF on
    // their phone. Word/Office formats are zip-based and need a different magic-byte
    // check; not worth the extra surface until a real need for them shows up.
    private val DOCUMENT_CONTENT_TYPES = setOf("application/pdf", "image/png", "image/jpeg")

    // Photos or short game clips a guardian uploads for their own household — no
    // thumbnail/resize pipeline exists for any slot in this app yet (2026-08-13
    // founder decision: ship without one, revisit if real usage demands it), so
    // photos render full-size via the existing signed-URL pattern and video gets a
    // generic icon in the UI rather than an extracted frame.
    private val HOUSEHOLD_MEDIA_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/webp", "video/mp4", "video/quicktime")
    private val HOUSEHOLD_MEDIA_VIDEO_CONTENT_TYPES = setOf("video/mp4", "video/quicktime")

    // Help Center article attachments (Track 4, 2026-08-13) — a platform admin
    // authoring/enriching an article can attach an image, an animated GIF (explicitly
    // requested, unlike any other slot), a short video clip, or a PDF handout. Reuses
    // the household media video cap and the document PDF cap rather than inventing new
    // numbers for the same underlying file classes.
    private val ARTICLE_ATTACHMENT_CONTENT_TYPES =
        setOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/quicktime", "application/pdf")

    fun allowedContentTypes(slot: MediaUsageSlot): Set<String> =
        when (slot) {
            MediaUsageSlot.LOGO -> LOGO_CONTENT_TYPES
            MediaUsageSlot.COVER -> COVER_CONTENT_TYPES
            MediaUsageSlot.PROFILE_PHOTO -> PROFILE_PHOTO_CONTENT_TYPES
            MediaUsageSlot.PRODUCT_DESIGN -> PRODUCT_DESIGN_CONTENT_TYPES
            MediaUsageSlot.SPONSOR_LOGO -> SPONSOR_LOGO_CONTENT_TYPES
            MediaUsageSlot.DOCUMENT -> DOCUMENT_CONTENT_TYPES
            MediaUsageSlot.HOUSEHOLD_MEDIA -> HOUSEHOLD_MEDIA_CONTENT_TYPES
            MediaUsageSlot.ARTICLE_ATTACHMENT -> ARTICLE_ATTACHMENT_CONTENT_TYPES
        }

    fun isVideoContentType(contentType: String): Boolean = contentType in HOUSEHOLD_MEDIA_VIDEO_CONTENT_TYPES

    fun isContentTypeAllowed(
        slot: MediaUsageSlot,
        contentType: String,
    ): Boolean = contentType in allowedContentTypes(slot)

    fun maxBytes(
        slot: MediaUsageSlot,
        contentType: String,
    ): Long =
        when (slot) {
            MediaUsageSlot.LOGO -> if (contentType == "image/svg+xml") MAX_LOGO_SVG_BYTES else MAX_LOGO_RASTER_BYTES
            MediaUsageSlot.COVER -> MAX_COVER_BYTES
            MediaUsageSlot.PROFILE_PHOTO -> MAX_PROFILE_PHOTO_BYTES
            MediaUsageSlot.PRODUCT_DESIGN ->
                if (contentType ==
                    "image/svg+xml"
                ) {
                    MAX_PRODUCT_DESIGN_SVG_BYTES
                } else {
                    MAX_PRODUCT_DESIGN_RASTER_BYTES
                }
            MediaUsageSlot.SPONSOR_LOGO -> if (contentType == "image/svg+xml") MAX_LOGO_SVG_BYTES else MAX_LOGO_RASTER_BYTES
            MediaUsageSlot.DOCUMENT -> MAX_DOCUMENT_BYTES
            MediaUsageSlot.HOUSEHOLD_MEDIA -> if (isVideoContentType(contentType)) MAX_HOUSEHOLD_MEDIA_VIDEO_BYTES else MAX_HOUSEHOLD_MEDIA_IMAGE_BYTES
            MediaUsageSlot.ARTICLE_ATTACHMENT ->
                when {
                    contentType == "application/pdf" -> MAX_DOCUMENT_BYTES
                    isVideoContentType(contentType) -> MAX_HOUSEHOLD_MEDIA_VIDEO_BYTES
                    else -> MAX_HOUSEHOLD_MEDIA_IMAGE_BYTES
                }
        }

    /** True for any content type this slot allows that isn't a decodable raster/vector image — e.g. a DOCUMENT slot's PDF, or a HOUSEHOLD_MEDIA video. Callers use this to skip image-specific validation (dimension decode/cap) that doesn't apply. */
    fun isNonImageContentType(contentType: String): Boolean = contentType == "application/pdf" || isVideoContentType(contentType)

    /**
     * Sniffs magic bytes to determine the actual image format, independent of the
     * client-declared content type. Returns null if the bytes don't match any
     * recognized format — callers should reject rather than trust the declared type.
     */
    fun detectContentType(bytes: ByteArray): String? {
        if (isPng(bytes)) return "image/png"
        if (isJpeg(bytes)) return "image/jpeg"
        if (isWebp(bytes)) return "image/webp"
        if (isGif(bytes)) return "image/gif"
        if (isSvg(bytes)) return "image/svg+xml"
        if (isPdf(bytes)) return "application/pdf"
        detectIsoBaseMediaVideoType(bytes)?.let { return it }
        return null
    }

    /**
     * MP4/MOV are both ISO Base Media File Format containers — a "ftyp" box at byte
     * offset 4 with a 4-byte major-brand code at offset 8. "qt  " (QuickTime's own
     * brand, space-padded to 4 chars) means MOV; every other real-world brand
     * (isom/mp42/mp41/avc1/M4V etc.) is treated as MP4 — phone-recorded game clips,
     * this feature's actual target, are overwhelmingly one of these two.
     */
    private fun detectIsoBaseMediaVideoType(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val boxType = String(bytes, 4, 4, Charsets.US_ASCII)
        if (boxType != "ftyp") return null
        val majorBrand = String(bytes, 8, 4, Charsets.US_ASCII)
        return if (majorBrand == "qt  ") "video/quicktime" else "video/mp4"
    }

    private fun isPdf(bytes: ByteArray): Boolean {
        val signature = "%PDF-".toByteArray(Charsets.US_ASCII)
        return bytes.size >= signature.size && bytes.copyOf(signature.size).contentEquals(signature)
    }

    private fun isPng(bytes: ByteArray): Boolean {
        val signature =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
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

    /** "GIF87a" or "GIF89a" — the only two real-world GIF version signatures. */
    private fun isGif(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        val header = String(bytes, 0, 6, Charsets.US_ASCII)
        return header == "GIF87a" || header == "GIF89a"
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

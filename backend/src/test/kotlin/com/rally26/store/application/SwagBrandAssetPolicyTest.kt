package com.rally26.store.application

import com.rally26.common.error.ValidationException
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.store.domain.SwagBrandAsset
import com.rally26.store.domain.SwagBrandAssetCategory
import com.rally26.store.domain.SwagBrandAssetStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SwagBrandAssetPolicyTest {
    private val organizationId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()

    @Test
    fun `organization asset is available to a team product`() {
        SwagBrandAssetPolicy.requireProductUse(asset(teamId = null), teamId)
    }

    @Test
    fun `team asset cannot cross teams`() {
        assertFailsWith<ValidationException> {
            SwagBrandAssetPolicy.requireProductUse(asset(teamId = teamId), UUID.randomUUID())
        }
    }

    @Test
    fun `archived asset cannot be newly assigned`() {
        assertFailsWith<ValidationException> {
            SwagBrandAssetPolicy.requireProductUse(asset(status = SwagBrandAssetStatus.ARCHIVED), teamId)
        }
    }

    @Test
    fun `ready raster logo is printable`() {
        SwagBrandAssetPolicy.requirePrintableMedia(media())
    }

    @Test
    fun `svg brand asset is rejected until print support exists`() {
        assertFailsWith<ValidationException> {
            SwagBrandAssetPolicy.requirePrintableMedia(media(contentType = "image/svg+xml"))
        }
    }

    @Test
    fun `unfinished media is rejected`() {
        assertFailsWith<ValidationException> {
            SwagBrandAssetPolicy.requirePrintableMedia(media(status = MediaAssetStatus.PROCESSING))
        }
    }

    private fun asset(
        teamId: UUID? = null,
        status: SwagBrandAssetStatus = SwagBrandAssetStatus.ACTIVE,
    ) = SwagBrandAsset(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        teamId = teamId,
        mediaAssetId = UUID.randomUUID(),
        name = "Primary crest",
        category = SwagBrandAssetCategory.PRIMARY,
        status = status,
        createdByUserId = UUID.randomUUID(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun media(
        contentType: String = "image/png",
        status: MediaAssetStatus = MediaAssetStatus.READY,
    ) = MediaAsset(
        id = UUID.randomUUID(),
        organizationId = organizationId,
        uploadedByUserId = UUID.randomUUID(),
        intendedUsageSlot = MediaUsageSlot.LOGO,
        originalFileName = "crest.png",
        declaredContentType = contentType,
        detectedContentType = contentType,
        storageKey = "organizations/$organizationId/media/crest.png",
        byteSize = 1024,
        checksumSha256 = null,
        widthPx = 1000,
        heightPx = 1000,
        status = status,
        rejectionReason = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}

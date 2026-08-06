package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.MediaAsset
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.membership.application.MembershipService
import com.rally26.store.domain.SwagBrandAsset
import com.rally26.store.domain.SwagBrandAssetCategory
import com.rally26.store.domain.SwagBrandAssetStatus
import com.rally26.store.persistence.SwagBrandAssetRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

data class SwagBrandAssetDescriptor(
    val asset: SwagBrandAsset,
    val previewUrl: String,
    val contentType: String,
    val widthPx: Int?,
    val heightPx: Int?,
)

@Service
class SwagBrandAssetService(
    private val repository: SwagBrandAssetRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val teamRepository: TeamRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val spacesClient: SpacesClient,
) {
    fun list(
        organizationId: UUID,
        teamId: UUID?,
        includeArchived: Boolean,
        currentUser: CurrentUser,
    ): List<SwagBrandAssetDescriptor> {
        requireManageAccess(organizationId, teamId, currentUser)
        return repository.findAvailable(organizationId, teamId, includeArchived).mapNotNull { describe(it) }
    }

    @Transactional
    fun create(
        organizationId: UUID,
        teamId: UUID?,
        mediaAssetId: UUID,
        name: String,
        category: SwagBrandAssetCategory,
        currentUser: CurrentUser,
    ): SwagBrandAssetDescriptor {
        requireManageAccess(organizationId, teamId, currentUser)
        val media = requirePrintableMedia(organizationId, mediaAssetId, currentUser.userId)
        val created =
            repository.insert(
                organizationId = organizationId,
                teamId = teamId,
                mediaAssetId = mediaAssetId,
                name = name.trim(),
                category = category,
                createdByUserId = currentUser.userId,
            )
        auditService.record(currentUser.userId, organizationId, "swag_brand_asset.created", "swag_brand_asset", created.id)
        return describe(created, media)
    }

    @Transactional
    fun archive(
        organizationId: UUID,
        assetId: UUID,
        currentUser: CurrentUser,
    ): SwagBrandAssetDescriptor {
        val asset = requireManageAccessForAsset(organizationId, assetId, currentUser)
        repository.updateStatus(assetId, organizationId, SwagBrandAssetStatus.ARCHIVED)
        auditService.record(currentUser.userId, organizationId, "swag_brand_asset.archived", "swag_brand_asset", assetId)
        return describe(requireAsset(organizationId, assetId)) ?: throw missingMedia()
    }

    @Transactional
    fun restore(
        organizationId: UUID,
        assetId: UUID,
        currentUser: CurrentUser,
    ): SwagBrandAssetDescriptor {
        val asset = requireManageAccessForAsset(organizationId, assetId, currentUser)
        val media = requirePrintableMedia(organizationId, asset.mediaAssetId)
        repository.updateStatus(assetId, organizationId, SwagBrandAssetStatus.ACTIVE)
        auditService.record(currentUser.userId, organizationId, "swag_brand_asset.restored", "swag_brand_asset", assetId)
        return describe(requireAsset(organizationId, assetId), media)
    }

    /** Used by ProductService after the product/store capability boundary is already checked. */
    fun requireActiveForProduct(
        organizationId: UUID,
        assetId: UUID,
        productTeamId: UUID?,
    ): SwagBrandAsset {
        val asset = requireAsset(organizationId, assetId)
        SwagBrandAssetPolicy.requireProductUse(asset, productTeamId)
        requirePrintableMedia(organizationId, asset.mediaAssetId)
        return asset
    }

    private fun requireManageAccessForAsset(
        organizationId: UUID,
        assetId: UUID,
        currentUser: CurrentUser,
    ): SwagBrandAsset {
        val asset = requireAsset(organizationId, assetId)
        requireManageAccess(organizationId, asset.teamId, currentUser)
        return asset
    }

    private fun requireManageAccess(
        organizationId: UUID,
        teamId: UUID?,
        currentUser: CurrentUser,
    ) {
        if (teamId == null) {
            membershipService.requireManagerRole(organizationId, currentUser)
            return
        }
        if (teamRepository.findById(teamId, organizationId) == null) {
            throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        }
        authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE)
    }

    private fun requireAsset(
        organizationId: UUID,
        assetId: UUID,
    ): SwagBrandAsset =
        repository.findById(assetId, organizationId)
            ?: throw NotFoundException("SWAG_BRAND_ASSET_NOT_FOUND", "The Swag Brand Asset could not be found.")

    private fun requirePrintableMedia(
        organizationId: UUID,
        mediaAssetId: UUID,
        requiredUploaderUserId: UUID? = null,
    ): MediaAsset {
        val media =
            mediaAssetRepository.findById(mediaAssetId, organizationId)
                ?: throw ValidationException("The uploaded brand image could not be found.")
        if (requiredUploaderUserId != null && media.uploadedByUserId != requiredUploaderUserId) {
            throw ValidationException("Choose a brand image uploaded during this setup session.")
        }
        SwagBrandAssetPolicy.requirePrintableMedia(media)
        return media
    }

    private fun describe(asset: SwagBrandAsset): SwagBrandAssetDescriptor? {
        val media = mediaAssetRepository.findById(asset.mediaAssetId, asset.organizationId) ?: return null
        return describe(asset, media)
    }

    private fun describe(
        asset: SwagBrandAsset,
        media: MediaAsset,
    ): SwagBrandAssetDescriptor =
        SwagBrandAssetDescriptor(
            asset = asset,
            previewUrl = spacesClient.presignedGetUrl(media.storageKey, Duration.ofMinutes(15)),
            contentType = media.detectedContentType ?: media.declaredContentType,
            widthPx = media.widthPx,
            heightPx = media.heightPx,
        )

    private fun missingMedia() = ValidationException("The Swag Brand Asset image could not be found.")
}

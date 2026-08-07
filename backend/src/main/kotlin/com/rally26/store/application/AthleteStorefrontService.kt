package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.store.domain.AthleteStorefront
import com.rally26.store.domain.AthleteStorefrontStatus
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.ProductVariant
import com.rally26.store.domain.StoreStatus
import com.rally26.store.domain.isValidAthleteStorefrontSlug
import com.rally26.store.persistence.AthleteStorefrontRepository
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Buyer-facing shape for `GET /public/athlete-storefronts/{slug}` — the real `Participant.lastName` is never serialized. */
data class AthleteStorefrontPublic(
    val storefront: AthleteStorefront,
    val athletePublicLabel: String,
)

/**
 * Phase 24 slice 24.3 (DESIGN-DOC.md section 14.1G). Lifecycle mirrors
 * `PublicPageService` exactly (create/publish/unpublish, slug validation,
 * `DuplicateKeyException` -> 409, `getPublic` never distinguishes
 * not-found from not-published). Manage access mirrors
 * `ProductService.requireStoreScopedManageAccess` (team-scoped coach or org
 * manager) since a storefront is created from a team's roster/store.
 */
@Service
class AthleteStorefrontService(
    private val repository: AthleteStorefrontRepository,
    private val storeRepository: StoreRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val participantRepository: ParticipantRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val qrCodeGenerator: QrCodeGenerator,
) {
    fun list(
        organizationId: UUID,
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<AthleteStorefront> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.findAll(organizationId, offset, limit)
    }

    fun count(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return repository.countAll(organizationId)
    }

    fun get(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return findOrThrow(storefrontId, organizationId)
    }

    fun listProductIds(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): List<UUID> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        findOrThrow(storefrontId, organizationId)
        return repository.listProductIds(storefrontId)
    }

    @Transactional
    fun create(
        organizationId: UUID,
        teamId: UUID?,
        participantId: UUID,
        storeId: UUID,
        productIds: List<UUID>,
        slug: String,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        requireStoreScopedManageAccess(organizationId, teamId, currentUser)
        validateSlug(slug)
        participantRepository.findById(participantId, organizationId)
            ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The participant could not be found.")
        if (teamId != null) {
            val onRoster =
                participantRepository
                    .listTeamAssignments(participantId, organizationId)
                    .any { it.teamId == teamId && it.status == "ACTIVE" }
            if (!onRoster) throw ValidationException("This athlete is not on the selected team's roster.")
        }
        val store =
            storeRepository.findById(storeId, organizationId)
                ?: throw NotFoundException("STORE_NOT_FOUND", "The Swag Shop could not be found.")
        if (store.status != StoreStatus.ACTIVE) throw ValidationException("This Swag Shop isn't currently active.")
        requireProductsInStore(organizationId, store.id, productIds)

        return try {
            val storefront = repository.insert(organizationId, participantId, teamId, storeId, slug)
            repository.replaceProducts(storefront.id, productIds)
            auditService.record(currentUser.userId, organizationId, "athlete_storefront.created", "athlete_storefront", storefront.id)
            storefront
        } catch (e: DuplicateKeyException) {
            throw ConflictException("ATHLETE_STOREFRONT_SLUG_TAKEN", "This slug is already in use.")
        }
    }

    @Transactional
    fun updateProducts(
        organizationId: UUID,
        storefrontId: UUID,
        productIds: List<UUID>,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        val storefront = requireManageAccessForStorefront(organizationId, storefrontId, currentUser)
        requireProductsInStore(organizationId, storefront.storeId, productIds)
        repository.replaceProducts(storefrontId, productIds)
        auditService.record(currentUser.userId, organizationId, "athlete_storefront.products_updated", "athlete_storefront", storefrontId)
        return findOrThrow(storefrontId, organizationId)
    }

    @Transactional
    fun publish(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        val storefront = requireManageAccessForStorefront(organizationId, storefrontId, currentUser)
        if (storefront.status == AthleteStorefrontStatus.PUBLISHED) return storefront
        if (storefront.status == AthleteStorefrontStatus.ARCHIVED) {
            throw ValidationException("An archived storefront cannot be published.")
        }
        if (repository.listProductIds(storefrontId).isEmpty()) {
            throw ValidationException("Select at least one product before publishing.")
        }
        repository.updateStatus(storefrontId, organizationId, AthleteStorefrontStatus.PUBLISHED, Instant.now())
        auditService.record(currentUser.userId, organizationId, "athlete_storefront.published", "athlete_storefront", storefrontId)
        return findOrThrow(storefrontId, organizationId)
    }

    @Transactional
    fun unpublish(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        val storefront = requireManageAccessForStorefront(organizationId, storefrontId, currentUser)
        if (storefront.status == AthleteStorefrontStatus.DRAFT) return storefront
        if (storefront.status == AthleteStorefrontStatus.ARCHIVED) {
            throw ValidationException("An archived storefront cannot be unpublished.")
        }
        repository.updateStatus(storefrontId, organizationId, AthleteStorefrontStatus.DRAFT, null)
        auditService.record(currentUser.userId, organizationId, "athlete_storefront.unpublished", "athlete_storefront", storefrontId)
        return findOrThrow(storefrontId, organizationId)
    }

    @Transactional
    fun archive(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        val storefront = requireManageAccessForStorefront(organizationId, storefrontId, currentUser)
        if (storefront.status == AthleteStorefrontStatus.ARCHIVED) return storefront
        repository.updateStatus(storefrontId, organizationId, AthleteStorefrontStatus.ARCHIVED, storefront.publishedAt)
        auditService.record(currentUser.userId, organizationId, "athlete_storefront.archived", "athlete_storefront", storefrontId)
        return findOrThrow(storefrontId, organizationId)
    }

    /** Public, unauthenticated. Never distinguishes "doesn't exist" from "not published," matching `PublicPageService.getPublic`/`StoreService.getPublic`. */
    fun getPublic(slug: String): AthleteStorefrontPublic {
        val storefront =
            repository.findBySlug(slug)
                ?: throw NotFoundException("ATHLETE_STOREFRONT_NOT_FOUND", "This storefront could not be found.")
        if (storefront.status != AthleteStorefrontStatus.PUBLISHED) {
            throw NotFoundException("ATHLETE_STOREFRONT_NOT_FOUND", "This storefront could not be found.")
        }
        val participant =
            participantRepository.findById(storefront.participantId, storefront.organizationId)
                ?: throw NotFoundException("ATHLETE_STOREFRONT_NOT_FOUND", "This storefront could not be found.")
        val lastInitial =
            participant.lastName
                .trim()
                .firstOrNull()
                ?.let { "$it." } ?: ""
        val athletePublicLabel = listOf(participant.firstName.trim(), lastInitial).filter { it.isNotEmpty() }.joinToString(" ")
        return AthleteStorefrontPublic(storefront, athletePublicLabel)
    }

    /** Public, unauthenticated — the storefront's approved products, filtered to still-ACTIVE (a product archived after being approved silently drops off, never 404s the whole page), each with its ACTIVE variants. */
    fun getPublicProducts(storefront: AthleteStorefront): List<Pair<Product, List<ProductVariant>>> =
        repository
            .listProductIds(storefront.id)
            .mapNotNull { productId -> productRepository.findById(productId, storefront.organizationId) }
            .filter { it.storeId == storefront.storeId && it.status == ProductStatus.ACTIVE }
            .map { product -> product to productVariantRepository.findActiveByProduct(product.id) }

    /** Admin-authenticated: renders a QR for a URL the frontend already knows (its own public storefront link) — no new QR concept, reuses `QrCodeGenerator` exactly like `SponsorshipPackageService.buildShareLink`. */
    fun buildShareLink(
        organizationId: UUID,
        url: String,
        currentUser: CurrentUser,
    ): String {
        membershipService.requireActiveMembership(organizationId, currentUser)
        if (url.length > 2000 || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw ValidationException("A valid http(s) URL is required to generate a QR code.")
        }
        return qrCodeGenerator.generatePngDataUri(url)
    }

    private fun requireProductsInStore(
        organizationId: UUID,
        storeId: UUID,
        productIds: List<UUID>,
    ) {
        if (productIds.isEmpty()) throw ValidationException("Select at least one product for this storefront.")
        productIds.forEach { productId ->
            val product =
                productRepository.findById(productId, organizationId)
                    ?: throw NotFoundException("PRODUCT_NOT_FOUND", "A selected product could not be found.")
            if (product.storeId != storeId || product.status != ProductStatus.ACTIVE) {
                throw ValidationException("A selected product isn't an active product of this Swag Shop.")
            }
        }
    }

    private fun requireManageAccessForStorefront(
        organizationId: UUID,
        storefrontId: UUID,
        currentUser: CurrentUser,
    ): AthleteStorefront {
        val storefront = findOrThrow(storefrontId, organizationId)
        requireStoreScopedManageAccess(organizationId, storefront.teamId, currentUser)
        return storefront
    }

    private fun requireStoreScopedManageAccess(
        organizationId: UUID,
        teamId: UUID?,
        currentUser: CurrentUser,
    ) {
        if (teamId != null) {
            authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE)
        } else {
            membershipService.requireManagerRole(organizationId, currentUser)
        }
    }

    private fun findOrThrow(
        storefrontId: UUID,
        organizationId: UUID,
    ): AthleteStorefront =
        repository.findById(storefrontId, organizationId)
            ?: throw NotFoundException("ATHLETE_STOREFRONT_NOT_FOUND", "The storefront could not be found.")

    private fun validateSlug(slug: String) {
        if (!isValidAthleteStorefrontSlug(slug)) {
            throw ValidationException(
                "Slug must be lowercase alphanumeric with optional hyphens.",
                listOf(
                    com.rally26.common.error
                        .FieldError("slug", "Invalid slug format."),
                ),
            )
        }
    }
}

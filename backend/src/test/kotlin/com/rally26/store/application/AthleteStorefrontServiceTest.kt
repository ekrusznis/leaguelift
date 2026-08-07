package com.rally26.store.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.participant.domain.Participant
import com.rally26.participant.domain.ParticipantStatus
import com.rally26.participant.domain.ParticipantTeamAssignment
import com.rally26.participant.persistence.ParticipantRepository
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.store.domain.AthleteStorefront
import com.rally26.store.domain.AthleteStorefrontStatus
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.AthleteStorefrontRepository
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.store.persistence.StoreRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AthleteStorefrontServiceTest {
    private val repository = mockk<AthleteStorefrontRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val productVariantRepository = mockk<ProductVariantRepository>()
    private val participantRepository = mockk<ParticipantRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val qrCodeGenerator = mockk<QrCodeGenerator>()
    private val service =
        AthleteStorefrontService(
            repository,
            storeRepository,
            productRepository,
            productVariantRepository,
            participantRepository,
            membershipService,
            authorizationService,
            auditService,
            qrCodeGenerator,
        )

    private val orgId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val storeId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

    @Test
    fun `create rejects an invalid slug`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs

        assertFailsWith<ValidationException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "Invalid Slug!", currentUser)
        }
    }

    @Test
    fun `create rejects a participant not on the selected team's roster`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns emptyList()

        assertFailsWith<ValidationException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `create rejects a participant that does not exist`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `create rejects an inactive store`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(sampleAssignment(status = "ACTIVE"))
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.DRAFT)

        assertFailsWith<ValidationException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `create rejects a product that is not active in this store`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(sampleAssignment(status = "ACTIVE"))
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.ACTIVE)
        every { productRepository.findById(productId, orgId) } returns sampleProduct(status = ProductStatus.DRAFT)

        assertFailsWith<ValidationException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `create rejects an empty product selection`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(sampleAssignment(status = "ACTIVE"))
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.ACTIVE)

        assertFailsWith<ValidationException> {
            service.create(orgId, teamId, participantId, storeId, emptyList(), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `create succeeds for a roster athlete with active store and products, and records audit`() {
        val storefront = sampleStorefront()
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(sampleAssignment(status = "ACTIVE"))
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.ACTIVE)
        every { productRepository.findById(productId, orgId) } returns sampleProduct(status = ProductStatus.ACTIVE)
        every { repository.insert(orgId, participantId, teamId, storeId, "maya-johnson") } returns storefront
        every { repository.replaceProducts(storefront.id, listOf(productId)) } just runs
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)

        assertEquals(storefront.id, result.id)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "athlete_storefront.created", "athlete_storefront", storefront.id, any())
        }
    }

    @Test
    fun `create with no team skips roster validation but still requires manager role`() {
        val storefront = sampleStorefront(teamId = null)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.ACTIVE)
        every { productRepository.findById(productId, orgId) } returns sampleProduct(status = ProductStatus.ACTIVE)
        every { repository.insert(orgId, participantId, null, storeId, "maya-johnson") } returns storefront
        every { repository.replaceProducts(storefront.id, listOf(productId)) } just runs
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, null, participantId, storeId, listOf(productId), "maya-johnson", currentUser)

        assertEquals(storefront.id, result.id)
    }

    @Test
    fun `create maps a duplicate slug into ConflictException`() {
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()
        every { participantRepository.listTeamAssignments(participantId, orgId) } returns
            listOf(sampleAssignment(status = "ACTIVE"))
        every { storeRepository.findById(storeId, orgId) } returns sampleStore(status = StoreStatus.ACTIVE)
        every { productRepository.findById(productId, orgId) } returns sampleProduct(status = ProductStatus.ACTIVE)
        every { repository.insert(orgId, participantId, teamId, storeId, "maya-johnson") } throws DuplicateKeyException("dup")

        assertFailsWith<ConflictException> {
            service.create(orgId, teamId, participantId, storeId, listOf(productId), "maya-johnson", currentUser)
        }
    }

    @Test
    fun `publish rejects an empty product selection`() {
        val storefront = sampleStorefront()
        every { repository.findById(storefront.id, orgId) } returns storefront
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { repository.listProductIds(storefront.id) } returns emptyList()

        assertFailsWith<ValidationException> {
            service.publish(orgId, storefront.id, currentUser)
        }
    }

    @Test
    fun `publish transitions DRAFT to PUBLISHED and records audit`() {
        val storefront = sampleStorefront()
        val published = storefront.copy(status = AthleteStorefrontStatus.PUBLISHED, publishedAt = Instant.now())
        every { repository.findById(storefront.id, orgId) } returnsMany listOf(storefront, published)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { repository.listProductIds(storefront.id) } returns listOf(productId)
        every { repository.updateStatus(storefront.id, orgId, AthleteStorefrontStatus.PUBLISHED, any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.publish(orgId, storefront.id, currentUser)

        assertEquals(AthleteStorefrontStatus.PUBLISHED, result.status)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "athlete_storefront.published", "athlete_storefront", storefront.id, any())
        }
    }

    @Test
    fun `publish is a no-op for an already-published storefront`() {
        val published = sampleStorefront().copy(status = AthleteStorefrontStatus.PUBLISHED, publishedAt = Instant.now())
        every { repository.findById(published.id, orgId) } returns published
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs

        val result = service.publish(orgId, published.id, currentUser)

        assertEquals(AthleteStorefrontStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish rejects an archived storefront`() {
        val archived = sampleStorefront().copy(status = AthleteStorefrontStatus.ARCHIVED)
        every { repository.findById(archived.id, orgId) } returns archived
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs

        assertFailsWith<ValidationException> {
            service.publish(orgId, archived.id, currentUser)
        }
    }

    @Test
    fun `unpublish transitions PUBLISHED to DRAFT and clears publishedAt`() {
        val published = sampleStorefront().copy(status = AthleteStorefrontStatus.PUBLISHED, publishedAt = Instant.now())
        val draft = sampleStorefront().copy(status = AthleteStorefrontStatus.DRAFT, publishedAt = null)
        every { repository.findById(published.id, orgId) } returnsMany listOf(published, draft)
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs
        every { repository.updateStatus(published.id, orgId, AthleteStorefrontStatus.DRAFT, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.unpublish(orgId, published.id, currentUser)

        assertEquals(AthleteStorefrontStatus.DRAFT, result.status)
    }

    @Test
    fun `archive is idempotent for an already-archived storefront`() {
        val archived = sampleStorefront().copy(status = AthleteStorefrontStatus.ARCHIVED)
        every { repository.findById(archived.id, orgId) } returns archived
        every { authorizationService.requireTeamCapability(orgId, teamId, currentUser, Capabilities.TEAM_STORE_MANAGE) } just runs

        val result = service.archive(orgId, archived.id, currentUser)

        assertEquals(AthleteStorefrontStatus.ARCHIVED, result.status)
    }

    @Test
    fun `getPublic throws NotFoundException for a draft storefront`() {
        every { repository.findBySlug("maya-johnson") } returns sampleStorefront()

        assertFailsWith<NotFoundException> {
            service.getPublic("maya-johnson")
        }
    }

    @Test
    fun `getPublic throws NotFoundException for an unknown slug`() {
        every { repository.findBySlug("does-not-exist") } returns null

        assertFailsWith<NotFoundException> {
            service.getPublic("does-not-exist")
        }
    }

    @Test
    fun `getPublic computes a first-name-plus-last-initial label and never exposes the real last name`() {
        val published = sampleStorefront().copy(status = AthleteStorefrontStatus.PUBLISHED, publishedAt = Instant.now())
        every { repository.findBySlug("maya-johnson") } returns published
        every { participantRepository.findById(participantId, orgId) } returns sampleParticipant()

        val result = service.getPublic("maya-johnson")

        assertEquals("Maya J.", result.athletePublicLabel)
        assert(!result.athletePublicLabel.contains("Johnson"))
    }

    @Test
    fun `getPublicProducts filters to still-active products in the storefront's own store`() {
        val storefront = sampleStorefront().copy(status = AthleteStorefrontStatus.PUBLISHED)
        val otherStoreProduct = sampleProduct(status = ProductStatus.ACTIVE).copy(id = UUID.randomUUID(), storeId = UUID.randomUUID())
        val archivedProduct = sampleProduct(status = ProductStatus.ARCHIVED).copy(id = UUID.randomUUID())
        val activeProduct = sampleProduct(status = ProductStatus.ACTIVE)
        every { repository.listProductIds(storefront.id) } returns listOf(activeProduct.id, otherStoreProduct.id, archivedProduct.id)
        every { productRepository.findById(activeProduct.id, orgId) } returns activeProduct
        every { productRepository.findById(otherStoreProduct.id, orgId) } returns otherStoreProduct
        every { productRepository.findById(archivedProduct.id, orgId) } returns archivedProduct
        every { productVariantRepository.findActiveByProduct(activeProduct.id) } returns emptyList()

        val result = service.getPublicProducts(storefront)

        assertEquals(1, result.size)
        assertEquals(activeProduct.id, result.single().first.id)
    }

    @Test
    fun `buildShareLink rejects a non-http url`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.buildShareLink(orgId, "ftp://example.com", currentUser)
        }
    }

    @Test
    fun `buildShareLink returns a data uri for a valid url`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { qrCodeGenerator.generatePngDataUri("https://rally26.com/swag-shop/athlete/maya-johnson") } returns
            "data:image/png;base64,abc"

        val result = service.buildShareLink(orgId, "https://rally26.com/swag-shop/athlete/maya-johnson", currentUser)

        assertEquals("data:image/png;base64,abc", result)
    }

    private fun sampleStorefront(teamId: UUID? = this.teamId) =
        AthleteStorefront(
            id = UUID.randomUUID(),
            organizationId = orgId,
            participantId = participantId,
            teamId = teamId,
            storeId = storeId,
            slug = "maya-johnson",
            status = AthleteStorefrontStatus.DRAFT,
            publishedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleParticipant() =
        Participant(
            id = participantId,
            householdId = UUID.randomUUID(),
            organizationId = orgId,
            firstName = "Maya",
            lastName = "Johnson",
            dateOfBirth = null,
            notes = null,
            status = ParticipantStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleAssignment(status: String) =
        ParticipantTeamAssignment(
            id = UUID.randomUUID(),
            participantId = participantId,
            teamId = teamId,
            organizationId = orgId,
            status = status,
            joinedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleStore(status: StoreStatus) =
        Store(
            id = storeId,
            organizationId = orgId,
            teamId = teamId,
            name = "Riverside Swag Shop",
            slug = "riverside-swag-shop",
            status = status,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun sampleProduct(status: ProductStatus) =
        Product(
            id = productId,
            organizationId = orgId,
            storeId = storeId,
            name = "Youth Hoodie",
            description = null,
            catalogSource = CatalogSource.PRINTIFY,
            manualVendorId = null,
            manualVendorName = null,
            printifyBlueprintId = 77L,
            printifyImageId = null,
            printifyPrintPosition = "front",
            status = status,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun managerMembership() =
        OrganizationMembership(
            id = UUID.randomUUID(),
            organizationId = orgId,
            userId = currentUser.userId,
            role = MembershipRole.ADMINISTRATOR,
            status = MembershipStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}

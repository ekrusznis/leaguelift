package com.rally26.sponsorship.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaAssignmentService
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.sponsorship.domain.Sponsor
import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageStatus
import com.rally26.sponsorship.domain.effectiveMaxQuantity
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SponsorshipPackageServiceTest {
    private val sponsorshipPackageRepository = mockk<SponsorshipPackageRepository>()
    private val sponsorRepository = mockk<SponsorRepository>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val mediaAssignmentService = mockk<MediaAssignmentService>()
    private val qrCodeGenerator = mockk<QrCodeGenerator>()
    private val service =
        SponsorshipPackageService(
            sponsorshipPackageRepository,
            sponsorRepository,
            organizationRepository,
            membershipService,
            auditService,
            mediaAssignmentService,
            qrCodeGenerator,
        )

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `create requires manager role and records audit`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        val created = samplePackage()
        every {
            sponsorshipPackageRepository.insert(orgId, "Gold Sponsor", null, 50_000L, "USD", 3, false, null, null)
        } returns created
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, "Gold Sponsor", null, 50_000L, "USD", 3, false, null, null, currentUser)

        assertEquals(created.id, result.id)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "sponsorship_package.created", "sponsorship_package", created.id, any())
        }
    }

    @Test
    fun `create rejects a zero max quantity`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(orgId, "Gold Sponsor", null, 50_000L, "USD", 0, false, null, null, currentUser)
        }
    }

    @Test
    fun `create rejects a placement end date before the start date`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(
                orgId,
                "Gold Sponsor",
                null,
                50_000L,
                "USD",
                null,
                false,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1),
                currentUser,
            )
        }
    }

    @Test
    fun `get throws NotFoundException when the package does not exist`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { sponsorshipPackageRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    @Test
    fun `listPublic resolves the organization by slug and returns only published packages`() {
        val org = sampleOrganization()
        every { organizationRepository.findBySlug("riverside-fc") } returns org
        every { sponsorshipPackageRepository.findPublished(org.id) } returns
            listOf(samplePackage(status = SponsorshipPackageStatus.PUBLISHED))

        val result = service.listPublic("riverside-fc")

        assertEquals(1, result.size)
        assertEquals(SponsorshipPackageStatus.PUBLISHED, result.single().status)
    }

    @Test
    fun `listPublic throws NotFoundException for an unknown organization slug`() {
        every { organizationRepository.findBySlug("nope") } returns null

        assertFailsWith<NotFoundException> {
            service.listPublic("nope")
        }
    }

    @Test
    fun `publish transitions a draft package to published and records audit`() {
        val draft = samplePackage(status = SponsorshipPackageStatus.DRAFT)
        val published = draft.copy(status = SponsorshipPackageStatus.PUBLISHED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipPackageRepository.findById(draft.id, orgId) } returns draft andThen published
        every { sponsorshipPackageRepository.updateStatus(draft.id, orgId, SponsorshipPackageStatus.PUBLISHED) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.publish(orgId, draft.id, currentUser)

        assertEquals(SponsorshipPackageStatus.PUBLISHED, result.status)
        verify(exactly = 1) {
            auditService.record(currentUser.userId, orgId, "sponsorship_package.published", "sponsorship_package", draft.id, any())
        }
    }

    @Test
    fun `publish is idempotent and does not re-record audit when already published`() {
        val published = samplePackage(status = SponsorshipPackageStatus.PUBLISHED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipPackageRepository.findById(published.id, orgId) } returns published

        val result = service.publish(orgId, published.id, currentUser)

        assertEquals(SponsorshipPackageStatus.PUBLISHED, result.status)
        verify(exactly = 0) { auditService.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `publish throws ValidationException for an archived package`() {
        val archived = samplePackage(status = SponsorshipPackageStatus.ARCHIVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorshipPackageRepository.findById(archived.id, orgId) } returns archived

        assertFailsWith<ValidationException> {
            service.publish(orgId, archived.id, currentUser)
        }
    }

    @Test
    fun `effectiveMaxQuantity is forced to 1 for an exclusive package regardless of maxQuantity`() {
        val exclusive = samplePackage(maxQuantity = 10).copy(exclusive = true)
        assertEquals(1, exclusive.effectiveMaxQuantity())
    }

    @Test
    fun `effectiveMaxQuantity is null (uncapped) for a non-exclusive package with no maxQuantity`() {
        val uncapped = samplePackage(maxQuantity = null)
        assertEquals(null, uncapped.effectiveMaxQuantity())
    }

    @Test
    fun `effectiveMaxQuantity passes through maxQuantity for a non-exclusive package`() {
        val capped = samplePackage(maxQuantity = 5)
        assertEquals(5, capped.effectiveMaxQuantity())
    }

    @Test
    fun `a non-exclusive package with no maxQuantity is never sold out`() {
        assertFalse(isSoldOut(samplePackage(maxQuantity = null), confirmedCount = 1000L))
    }

    @Test
    fun `a capped package is sold out once confirmedCount reaches maxQuantity`() {
        assertTrue(isSoldOut(samplePackage(maxQuantity = 2), confirmedCount = 2L))
        assertFalse(isSoldOut(samplePackage(maxQuantity = 2), confirmedCount = 1L))
    }

    @Test
    fun `updateSponsor requires manager role, updates fields, and records audit`() {
        val sponsor = sampleSponsor()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorRepository.findById(sponsor.id, orgId) } returns sponsor andThen sponsor.copy(phone = "555-1212")
        every { sponsorRepository.update(sponsor.id, orgId, null, null, "555-1212", null, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.updateSponsor(orgId, sponsor.id, null, null, "555-1212", null, null, currentUser)

        assertEquals("555-1212", result.phone)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "sponsor.updated", "sponsor", sponsor.id, any()) }
    }

    @Test
    fun `updateSponsor throws NotFoundException for an unknown sponsor`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { sponsorRepository.findById(any<UUID>(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.updateSponsor(orgId, UUID.randomUUID(), "New Name", null, null, null, null, currentUser)
        }
    }

    @Test
    fun `buildShareLink rejects a non-http(s) url`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.buildShareLink(orgId, "javascript:alert(1)", currentUser)
        }
    }

    @Test
    fun `buildShareLink delegates to the QR generator for a valid url`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { qrCodeGenerator.generatePngDataUri("https://app.local/sponsors/riverside-fc") } returns "data:image/png;base64,ABC"

        val result = service.buildShareLink(orgId, "https://app.local/sponsors/riverside-fc", currentUser)

        assertEquals("data:image/png;base64,ABC", result)
    }

    private fun sampleSponsor() =
        Sponsor(
            id = UUID.randomUUID(),
            organizationId = orgId,
            name = "Acme Co",
            contactEmail = "sponsor@acme.test",
            phone = null,
            companyName = null,
            notes = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun isSoldOut(
        sponsorshipPackage: SponsorshipPackage,
        confirmedCount: Long,
    ): Boolean = sponsorshipPackage.effectiveMaxQuantity()?.let { confirmedCount >= it } ?: false

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

    private fun sampleOrganization() =
        Organization(
            id = orgId,
            name = "Riverside FC",
            slug = "riverside-fc",
            organizationType = OrganizationType.RECREATIONAL_LEAGUE,
            status = OrganizationStatus.ACTIVE,
            sports = emptyList(),
            contactEmail = null,
            contactPhone = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun samplePackage(
        status: SponsorshipPackageStatus = SponsorshipPackageStatus.DRAFT,
        maxQuantity: Int? = 3,
    ) = SponsorshipPackage(
        id = UUID.randomUUID(),
        organizationId = orgId,
        name = "Gold Sponsor",
        description = null,
        priceMinor = 50_000L,
        currency = "USD",
        maxQuantity = maxQuantity,
        exclusive = false,
        placementStartDate = null,
        placementEndDate = null,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}

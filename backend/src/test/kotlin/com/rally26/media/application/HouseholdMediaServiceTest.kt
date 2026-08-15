package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HouseholdMediaServiceTest {
    private val mediaAssetRepository = mockk<MediaAssetRepository>()
    private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
    private val membershipService = mockk<MembershipService>()
    private val authorizationService = mockk<AuthorizationService>()
    private val householdRepository = mockk<HouseholdRepository>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val outboxWriter = mockk<OutboxWriter>(relaxed = true)

    private val service =
        HouseholdMediaService(
            mediaAssetRepository,
            mediaAssignmentRepository,
            membershipService,
            authorizationService,
            householdRepository,
            auditService,
            outboxWriter,
        )

    private val orgId = UUID.randomUUID()
    private val householdId = UUID.randomUUID()
    private val guardian = CurrentUser(UUID.randomUUID(), "guardian@example.com", "Guardian")
    private val stranger = CurrentUser(UUID.randomUUID(), "stranger@example.com", "Stranger")

    private fun asset(
        id: UUID = UUID.randomUUID(),
        status: MediaAssetStatus = MediaAssetStatus.READY,
        slot: MediaUsageSlot = MediaUsageSlot.HOUSEHOLD_MEDIA,
        contentType: String = "image/png",
    ) = MediaAsset(
        id,
        orgId,
        guardian.userId,
        slot,
        "game.png",
        contentType,
        contentType,
        "key/$id",
        1024L,
        "checksum",
        800,
        600,
        status,
        null,
        Instant.now(),
        Instant.now(),
    )

    private fun assignment(
        id: UUID = UUID.randomUUID(),
        assetId: UUID = UUID.randomUUID(),
        status: PublicationStatus = PublicationStatus.APPROVED,
        visibility: Visibility = Visibility.HOUSEHOLD_PRIVATE,
    ) = MediaAssignment(
        id,
        orgId,
        assetId,
        MediaEntityType.HOUSEHOLD,
        householdId,
        MediaUsageSlot.HOUSEHOLD_MEDIA,
        status,
        visibility,
        null,
        Instant.now(),
        Instant.now(),
    )

    private fun allowGuardian() {
        every { membershipService.hasManagerRole(orgId, guardian) } returns false
        every { authorizationService.hasGuardianRelationship(orgId, householdId, guardian) } returns true
    }

    private fun denyStranger() {
        every { membershipService.hasManagerRole(orgId, stranger) } returns false
        every { authorizationService.hasGuardianRelationship(orgId, householdId, stranger) } returns false
    }

    @Test
    fun `list denies a stranger with no guardian relationship or manager role`() {
        denyStranger()

        assertFailsWith<ForbiddenException> {
            service.list(orgId, householdId, stranger)
        }
    }

    @Test
    fun `list returns only HOUSEHOLD_MEDIA assignments for a guardian`() {
        allowGuardian()
        val media = assignment()
        every { mediaAssignmentRepository.listActive(MediaEntityType.HOUSEHOLD, householdId) } returns listOf(media)

        val result = service.list(orgId, householdId, guardian)

        assertEquals(listOf(media), result)
    }

    @Test
    fun `assign lets a guardian (not just a manager) add their own household's media`() {
        allowGuardian()
        every { householdRepository.findById(householdId, orgId) } returns
            com.rally26.household.domain.Household(
                householdId,
                orgId,
                "Smith Family",
                null,
                null,
                null,
                false,
                false,
                com.rally26.household.domain.HouseholdStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        val readyAsset = asset()
        every { mediaAssetRepository.findById(readyAsset.id, orgId) } returns readyAsset
        every {
            mediaAssignmentRepository.insert(
                organizationId = orgId,
                assetId = readyAsset.id,
                entityType = MediaEntityType.HOUSEHOLD,
                entityId = householdId,
                usageSlot = MediaUsageSlot.HOUSEHOLD_MEDIA,
                publicationStatus = PublicationStatus.APPROVED,
                visibility = Visibility.HOUSEHOLD_PRIVATE,
                altText = null,
            )
        } returns assignment(assetId = readyAsset.id)

        val result = service.assign(orgId, householdId, readyAsset.id, guardian)

        assertEquals(readyAsset.id, result.assetId)
        verify(exactly = 1) {
            auditService.record(guardian.userId, orgId, "household_media.added", "media_assignment", result.id, householdId = householdId)
        }
    }

    @Test
    fun `assign rejects an asset that is not READY`() {
        allowGuardian()
        every { householdRepository.findById(householdId, orgId) } returns
            com.rally26.household.domain.Household(
                householdId,
                orgId,
                "Smith Family",
                null,
                null,
                null,
                false,
                false,
                com.rally26.household.domain.HouseholdStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        val pending = asset(status = MediaAssetStatus.PENDING_UPLOAD)
        every { mediaAssetRepository.findById(pending.id, orgId) } returns pending

        assertFailsWith<ValidationException> {
            service.assign(orgId, householdId, pending.id, guardian)
        }
    }

    @Test
    fun `assign rejects an asset uploaded for a different slot`() {
        allowGuardian()
        every { householdRepository.findById(householdId, orgId) } returns
            com.rally26.household.domain.Household(
                householdId,
                orgId,
                "Smith Family",
                null,
                null,
                null,
                false,
                false,
                com.rally26.household.domain.HouseholdStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
            )
        val documentAsset = asset(slot = MediaUsageSlot.DOCUMENT)
        every { mediaAssetRepository.findById(documentAsset.id, orgId) } returns documentAsset

        assertFailsWith<ValidationException> {
            service.assign(orgId, householdId, documentAsset.id, guardian)
        }
    }

    @Test
    fun `remove denies a stranger`() {
        denyStranger()

        assertFailsWith<ForbiddenException> {
            service.remove(orgId, householdId, UUID.randomUUID(), stranger)
        }
    }

    @Test
    fun `remove retires the assignment for a guardian`() {
        allowGuardian()
        val media = assignment()
        every { mediaAssignmentRepository.findById(media.id, orgId) } returns media
        every { mediaAssignmentRepository.retire(media.id, orgId) } returns 1

        service.remove(orgId, householdId, media.id, guardian)

        verify(exactly = 1) { mediaAssignmentRepository.retire(media.id, orgId) }
    }

    @Test
    fun `remove throws NotFoundException for an assignment belonging to a different household`() {
        allowGuardian()
        val otherHouseholdId = UUID.randomUUID()
        val media = assignment().copy(entityId = otherHouseholdId)
        every { mediaAssignmentRepository.findById(media.id, orgId) } returns media

        assertFailsWith<NotFoundException> {
            service.remove(orgId, householdId, media.id, guardian)
        }
    }

    @Test
    fun `releasePublicly publishes each assignment and fires one outbox event per asset`() {
        allowGuardian()
        val first = assignment()
        val second = assignment()
        every { mediaAssignmentRepository.findById(first.id, orgId) } returns first andThen
            first.copy(visibility = Visibility.PUBLIC, publicationStatus = PublicationStatus.PUBLISHED)
        every { mediaAssignmentRepository.findById(second.id, orgId) } returns second andThen
            second.copy(visibility = Visibility.PUBLIC, publicationStatus = PublicationStatus.PUBLISHED)
        every { mediaAssignmentRepository.publishPublicly(first.id, orgId) } returns 1
        every { mediaAssignmentRepository.publishPublicly(second.id, orgId) } returns 1

        val result = service.releasePublicly(orgId, householdId, listOf(first.id, second.id), guardian)

        assertEquals(2, result.size)
        verify(exactly = 1) { mediaAssignmentRepository.publishPublicly(first.id, orgId) }
        verify(exactly = 1) { mediaAssignmentRepository.publishPublicly(second.id, orgId) }
        verify(exactly = 2) { outboxWriter.write(any(), any(), any(), eq("media.assignment.published"), any()) }
        verify(exactly = 2) {
            auditService.record(
                guardian.userId,
                orgId,
                "household_media.released_publicly",
                "media_assignment",
                any(),
                householdId = householdId,
            )
        }
    }

    @Test
    fun `releasePublicly denies a stranger`() {
        denyStranger()

        assertFailsWith<ForbiddenException> {
            service.releasePublicly(orgId, householdId, listOf(UUID.randomUUID()), stranger)
        }
    }
}

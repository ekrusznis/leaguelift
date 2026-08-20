package com.rally26.foundingorg.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.foundingorg.domain.FoundingOrgPromoCode
import com.rally26.foundingorg.domain.FoundingPilotStatus
import com.rally26.foundingorg.persistence.FoundingOrgPromoCodeRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FoundingPromoCodeServiceTest {
    private val repository = mockk<FoundingOrgPromoCodeRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val service = FoundingPromoCodeService(repository, authorizationService)

    private val platformAdmin = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)
    private val regularUser = CurrentUser(UUID.randomUUID(), "user@example.com", "User")

    private fun code(
        pilotStatus: FoundingPilotStatus,
        codeValue: String = "FOUNDING-ABC123",
    ) = FoundingOrgPromoCode(
        id = UUID.randomUUID(),
        code = codeValue,
        reservedByUserId = null,
        reservedAt = null,
        organizationId = null,
        redeemedAt = null,
        pilotEndsAt = null,
        pilotStatus = pilotStatus,
        nextReminderIndex = 0,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `generateCode requires the platform capability`() {
        every {
            authorizationService.requirePlatformCapability(regularUser, Capabilities.PLATFORM_FOUNDING_PROMO_MANAGE)
        } throws ForbiddenException("CAPABILITY_DENIED", "denied")

        assertFailsWith<ForbiddenException> {
            service.generateCode(regularUser)
        }
    }

    @Test
    fun `generateCode inserts a unique code for a platform admin`() {
        every { authorizationService.requirePlatformCapability(platformAdmin, Capabilities.PLATFORM_FOUNDING_PROMO_MANAGE) } returns Unit
        every { repository.existsWithCode(any()) } returns false
        val created = code(FoundingPilotStatus.UNREDEEMED)
        every { repository.insert(any()) } returns created

        val result = service.generateCode(platformAdmin)

        assertEquals(created, result)
        verify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun `validate returns valid for an unredeemed code`() {
        every { repository.findByCode("FOUNDING-ABC123") } returns code(FoundingPilotStatus.UNREDEEMED)

        val result = service.validate("foundING-abc123")

        assertEquals(true, result.valid)
    }

    @Test
    fun `validate returns invalid for a redeemed code`() {
        every { repository.findByCode("FOUNDING-ABC123") } returns code(FoundingPilotStatus.ACTIVE)

        val result = service.validate("FOUNDING-ABC123")

        assertEquals(false, result.valid)
    }

    @Test
    fun `validate returns invalid for an unrecognized code`() {
        every { repository.findByCode("FOUNDING-NOPE") } returns null

        val result = service.validate("FOUNDING-NOPE")

        assertEquals(false, result.valid)
    }

    @Test
    fun `reserve locks and reserves an unredeemed code`() {
        val userId = UUID.randomUUID()
        val found = code(FoundingPilotStatus.UNREDEEMED)
        every { repository.findByCodeForUpdate("FOUNDING-ABC123") } returns found
        every { repository.reserve(found.id, userId) } just runs

        service.reserve("founding-abc123", userId)

        verify(exactly = 1) { repository.reserve(found.id, userId) }
    }

    @Test
    fun `reserve rejects an already-reserved code`() {
        every { repository.findByCodeForUpdate("FOUNDING-ABC123") } returns code(FoundingPilotStatus.RESERVED)

        assertFailsWith<ValidationException> {
            service.reserve("FOUNDING-ABC123", UUID.randomUUID())
        }
    }

    @Test
    fun `reserve rejects an unrecognized code`() {
        every { repository.findByCodeForUpdate("FOUNDING-NOPE") } returns null

        assertFailsWith<NotFoundException> {
            service.reserve("FOUNDING-NOPE", UUID.randomUUID())
        }
    }
}

package com.rally26.foundingorg.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.foundingorg.domain.FoundingOrgPromoCode
import com.rally26.foundingorg.domain.FoundingPilotStatus
import com.rally26.foundingorg.persistence.FoundingOrgPromoCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

data class FoundingCodeValidation(
    val valid: Boolean,
    val reason: String? = null,
)

/** Excludes visually ambiguous characters (0/O, 1/I) from generated codes. */
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val CODE_SUFFIX_LENGTH = 6
private const val MAX_GENERATION_ATTEMPTS = 10

/**
 * Founding Organization pilot (founder-directed, 2026-08-20): a handful of single-use
 * codes granting free FOUNDING_CLUB access for a 90-day pilot, given directly to
 * hand-picked orgs rather than seeded/self-serve. See `founding_org_promo_code`
 * (migration V96) and [com.rally26.foundingorg.domain.FOUNDING_PILOT_REMINDER_SCHEDULE].
 */
@Service
class FoundingPromoCodeService(
    private val repository: FoundingOrgPromoCodeRepository,
    private val authorizationService: AuthorizationService,
) {
    private val random = SecureRandom()

    @Transactional
    fun generateCode(currentUser: CurrentUser): FoundingOrgPromoCode {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_FOUNDING_PROMO_MANAGE)
        repeat(MAX_GENERATION_ATTEMPTS) {
            val candidate =
                "FOUNDING-" + (1..CODE_SUFFIX_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            if (!repository.existsWithCode(candidate)) {
                return repository.insert(candidate)
            }
        }
        throw ConflictException("FOUNDING_PROMO_CODE_GENERATION_FAILED", "Could not generate a unique code. Try again.")
    }

    fun listCodes(currentUser: CurrentUser): List<FoundingOrgPromoCode> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_FOUNDING_PROMO_MANAGE)
        return repository.listAll()
    }

    /** Public — used by the join page before showing the registration form. */
    fun validate(code: String): FoundingCodeValidation {
        val found = repository.findByCode(code.trim().uppercase()) ?: return FoundingCodeValidation(false, "This code is not recognized.")
        return when (found.pilotStatus) {
            FoundingPilotStatus.UNREDEEMED -> FoundingCodeValidation(true)
            FoundingPilotStatus.RESERVED -> FoundingCodeValidation(false, "This code is already being used to register an organization.")
            FoundingPilotStatus.ACTIVE, FoundingPilotStatus.CONVERTED, FoundingPilotStatus.EXPIRED ->
                FoundingCodeValidation(false, "This code has already been redeemed.")
        }
    }

    /**
     * Called from [com.rally26.identity.application.PasswordAuthenticationService.registerOwner]
     * right after the new app_user row is created — before an organization exists, so this only
     * reserves the code against the new user, preventing two concurrent registrations from
     * racing on the same code. Real redemption (`organization_id`/`pilot_ends_at`) happens later,
     * at [com.rally26.onboarding.owner.application.OwnerOnboardingService.activateFoundingPromoPlan].
     */
    @Transactional
    fun reserve(
        code: String,
        userId: UUID,
    ) {
        val normalized = code.trim().uppercase()
        val found =
            repository.findByCodeForUpdate(normalized)
                ?: throw NotFoundException("FOUNDING_PROMO_CODE_NOT_FOUND", "This founding organization code is not recognized.")
        if (found.pilotStatus != FoundingPilotStatus.UNREDEEMED) {
            throw ValidationException("This founding organization code has already been used.")
        }
        repository.reserve(found.id, userId)
    }
}

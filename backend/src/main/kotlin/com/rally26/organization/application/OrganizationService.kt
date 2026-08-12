package com.rally26.organization.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.notification.AnalyticsEvent
import com.rally26.notification.AnalyticsProvider
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.domain.isValidContactEmail
import com.rally26.organization.domain.isValidSlug
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.payout.persistence.OrganizationPayoutAccountRepository
import com.rally26.timezone.application.TimeZoneService
import com.rally26.timezone.application.TimezoneSuggestionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val payoutAccountRepository: OrganizationPayoutAccountRepository,
    private val analyticsProvider: AnalyticsProvider,
    private val timeZoneService: TimeZoneService,
    private val timezoneSuggestionService: TimezoneSuggestionService,
) {
    @Transactional
    fun create(
        name: String,
        slug: String,
        organizationType: OrganizationType,
        currentUser: CurrentUser,
    ): Organization {
        if (!isValidSlug(slug)) {
            throw ValidationException(
                "Slug must be lowercase alphanumeric with optional hyphens.",
                listOf(
                    com.rally26.common.error
                        .FieldError("slug", "Invalid slug format."),
                ),
            )
        }
        organizationRepository.findBySlug(slug)?.let {
            throw ConflictException("ORGANIZATION_SLUG_TAKEN", "This organization slug is already in use.")
        }

        val organization = organizationRepository.insert(name, slug, organizationType)
        membershipService.grantOwner(organization.id, currentUser.userId)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organization.id,
            action = "organization.created",
            entityType = "organization",
            entityId = organization.id,
        )
        // The first real AnalyticsProvider call site (Phase 9, ADR-025) — org creation is
        // the most fundamental usage signal a "usage insights" tool would want. Only
        // the org type is worth including as a property; nothing else here is
        // non-sensitive enough to forward to a future real vendor by default.
        analyticsProvider.track(
            AnalyticsEvent(
                name = "organization_created",
                organizationId = organization.id,
                userId = currentUser.userId,
                properties = mapOf("organizationType" to organizationType.name),
            ),
        )
        return organization
    }

    fun get(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): Organization {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return organizationRepository.findById(organizationId)
            ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
    }

    fun listForUser(
        currentUser: CurrentUser,
        offset: Int,
        limit: Int,
    ): List<Organization> = organizationRepository.findForUser(currentUser.userId, offset, limit)

    fun countForUser(currentUser: CurrentUser): Long = organizationRepository.countForUser(currentUser.userId)

    @Transactional
    fun update(
        organizationId: UUID,
        name: String?,
        organizationType: OrganizationType?,
        sports: List<String>?,
        contactEmail: String?,
        contactPhone: String?,
        currentUser: CurrentUser,
        addressLine1: String? = null,
        addressLine2: String? = null,
        addressCity: String? = null,
        addressState: String? = null,
        addressPostalCode: String? = null,
        addressCountry: String? = null,
        /** Phase 24 slice 24.5 (ADR-071): a non-null value here IS the owner's timezone-confirmation signal — see [onboardingProgress]. */
        timezone: String? = null,
        zelleHandle: String? = null,
    ): Organization {
        membershipService.requireActiveMembership(organizationId, currentUser)
        organizationRepository.findById(organizationId)
            ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        if (contactEmail != null && !isValidContactEmail(contactEmail)) {
            throw ValidationException(
                "Contact email is not a valid email address.",
                listOf(
                    com.rally26.common.error
                        .FieldError("contactEmail", "Invalid email format."),
                ),
            )
        }
        if (timezone != null) timeZoneService.requireValid(timezone)
        organizationRepository.updateProfile(
            organizationId,
            name,
            organizationType,
            sports,
            contactEmail,
            contactPhone,
            addressLine1,
            addressLine2,
            addressCity,
            addressState,
            addressPostalCode,
            addressCountry,
            timezone,
            zelleHandle,
        )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "organization.updated",
            entityType = "organization",
            entityId = organizationId,
        )
        return organizationRepository.findById(organizationId)!!
    }

    /**
     * The create-organization-profile form's timezone suggestion (acceptance
     * criterion: "suggests an IANA timezone from the entered organization address
     * and requires confirmation") — a static heuristic (see
     * [TimezoneSuggestionService]), not a real geocoding call. Reads whatever address
     * is currently persisted; returns null if no address is set yet or the
     * country/state pair isn't recognized — the caller/frontend falls back to the
     * browser's own timezone as a last resort, never presenting this as authoritative.
     */
    fun suggestTimezone(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): String? {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val organization =
            organizationRepository.findById(organizationId)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        return timezoneSuggestionService.suggest(organization.addressCountry, organization.addressState)
    }

    /**
     * Onboarding checklist for the organization-owner setup flow (DESIGN-DOC.md
     * section 15.2). Only reflects modules that exist so far — team/tournament/public
     * page items are added here when those vertical slices ship (section 35), rather
     * than showing permanently-incomplete steps for modules that don't exist yet.
     */
    fun onboardingProgress(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OnboardingProgress {
        membershipService.requireActiveMembership(organizationId, currentUser)
        val organization =
            organizationRepository.findById(organizationId)
                ?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
        val profileComplete = organization.sports.isNotEmpty() && organization.contactEmail != null
        return OnboardingProgress(
            profileComplete = profileComplete,
            hasAdditionalAdministrator = membershipService.countMembers(organizationId) > 1,
            payoutsConnected = payoutAccountRepository.findByOrganizationId(organizationId)?.isFullyConnected ?: false,
            timezoneConfirmed = organization.timezone != null,
        )
    }
}

data class OnboardingProgress(
    val profileComplete: Boolean,
    val hasAdditionalAdministrator: Boolean,
    val payoutsConnected: Boolean,
    val timezoneConfirmed: Boolean,
)

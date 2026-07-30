package com.leaguelift.organization.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.notification.AnalyticsEvent
import com.leaguelift.notification.AnalyticsProvider
import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.organization.domain.isValidContactEmail
import com.leaguelift.organization.domain.isValidSlug
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.payout.persistence.OrganizationPayoutAccountRepository
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
) {

	@Transactional
	fun create(name: String, slug: String, organizationType: OrganizationType, currentUser: CurrentUser): Organization {
		if (!isValidSlug(slug)) {
			throw ValidationException(
				"Slug must be lowercase alphanumeric with optional hyphens.",
				listOf(com.leaguelift.common.error.FieldError("slug", "Invalid slug format.")),
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

	fun get(organizationId: UUID, currentUser: CurrentUser): Organization {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return organizationRepository.findById(organizationId)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
	}

	fun listForUser(currentUser: CurrentUser, offset: Int, limit: Int): List<Organization> =
		organizationRepository.findForUser(currentUser.userId, offset, limit)

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
	): Organization {
		membershipService.requireActiveMembership(organizationId, currentUser)
		organizationRepository.findById(organizationId)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
		if (contactEmail != null && !isValidContactEmail(contactEmail)) {
			throw ValidationException(
				"Contact email is not a valid email address.",
				listOf(com.leaguelift.common.error.FieldError("contactEmail", "Invalid email format.")),
			)
		}
		organizationRepository.updateProfile(organizationId, name, organizationType, sports, contactEmail, contactPhone)
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
	 * Onboarding checklist for the organization-owner setup flow (DESIGN-DOC.md
	 * section 15.2). Only reflects modules that exist so far — team/tournament/public
	 * page items are added here when those vertical slices ship (section 35), rather
	 * than showing permanently-incomplete steps for modules that don't exist yet.
	 */
	fun onboardingProgress(organizationId: UUID, currentUser: CurrentUser): OnboardingProgress {
		membershipService.requireActiveMembership(organizationId, currentUser)
		val organization = organizationRepository.findById(organizationId)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
		val profileComplete = organization.sports.isNotEmpty() && organization.contactEmail != null
		return OnboardingProgress(
			profileComplete = profileComplete,
			hasAdditionalAdministrator = membershipService.countMembers(organizationId) > 1,
			payoutsConnected = payoutAccountRepository.findByOrganizationId(organizationId)?.isFullyConnected ?: false,
		)
	}
}

data class OnboardingProgress(
	val profileComplete: Boolean,
	val hasAdditionalAdministrator: Boolean,
	val payoutsConnected: Boolean,
)

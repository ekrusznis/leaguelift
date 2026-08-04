package com.rally26.sponsorship.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.FieldError
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaAssignmentService
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.Visibility
import com.rally26.membership.application.MembershipService
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.sponsorship.domain.Sponsor
import com.rally26.sponsorship.domain.SponsorshipPackage
import com.rally26.sponsorship.domain.SponsorshipPackageStatus
import com.rally26.sponsorship.infra.QrCodeGenerator
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipPackageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Org-admin CRUD + publish for sponsorship packages (DESIGN-DOC.md section 14.1 Phase 6
 * slice 1) — mirrors `fundraising/application/CampaignService.kt`'s DRAFT -> PUBLISHED ->
 * ARCHIVED shape closely. Also owns sponsor-logo assignment: unlike a campaign
 * contribution, a sponsorship has an image (the sponsor's logo) to manage, and — since
 * the media pipeline's upload/assign endpoints are always manager-role-authenticated
 * (see `media/application/MediaUploadService.kt`) — that logo is uploaded/assigned by an
 * org admin after a sponsorship confirms, the same way `store/application/ProductService.kt`
 * manages a product's design image. There's no public/anonymous upload path anywhere in
 * this codebase yet, so a self-service "upload your own logo at checkout" flow for an
 * unauthenticated sponsor is out of scope for this slice (see ADR-018) — the public
 * checkout request only collects the sponsor's name/contact email.
 */
@Service
class SponsorshipPackageService(
	private val sponsorshipPackageRepository: SponsorshipPackageRepository,
	private val sponsorRepository: SponsorRepository,
	private val organizationRepository: OrganizationRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val mediaAssignmentService: MediaAssignmentService,
	private val qrCodeGenerator: QrCodeGenerator,
) {

	/** Allow-listed URL prefixes for [buildShareLink] — a defensive check, not a real security boundary (the QR image itself carries no risk), preventing a caller from turning this into an arbitrary-text QR generator. */
	private val allowedShareLinkPrefixes = listOf("http://", "https://")

	fun list(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<SponsorshipPackage> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return sponsorshipPackageRepository.findAll(organizationId, offset, limit)
	}

	fun count(organizationId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return sponsorshipPackageRepository.countAll(organizationId)
	}

	fun get(organizationId: UUID, packageId: UUID, currentUser: CurrentUser): SponsorshipPackage {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return findPackage(organizationId, packageId)
	}

	/** Public, unauthenticated — only PUBLISHED packages for an org, resolved by the org's own public slug (mirrors `StoreService.getPublic`/`CampaignService.getPublic`'s ACTIVE-only gate). */
	fun listPublic(organizationSlug: String): List<SponsorshipPackage> {
		val organization = organizationRepository.findBySlug(organizationSlug)
			?: throw NotFoundException("ORGANIZATION_NOT_FOUND", "The organization could not be found.")
		return sponsorshipPackageRepository.findPublished(organization.id)
	}

	@Transactional
	fun create(
		organizationId: UUID,
		name: String,
		description: String?,
		priceMinor: Long,
		currency: String,
		maxQuantity: Int?,
		exclusive: Boolean,
		placementStartDate: LocalDate?,
		placementEndDate: LocalDate?,
		currentUser: CurrentUser,
	): SponsorshipPackage {
		membershipService.requireManagerRole(organizationId, currentUser)
		validateMaxQuantity(maxQuantity)
		validateDateRange(placementStartDate, placementEndDate)
		val sponsorshipPackage = sponsorshipPackageRepository.insert(
			organizationId, name, description, priceMinor, currency, maxQuantity, exclusive, placementStartDate, placementEndDate,
		)
		auditService.record(currentUser.userId, organizationId, "sponsorship_package.created", "sponsorship_package", sponsorshipPackage.id)
		return sponsorshipPackage
	}

	@Transactional
	fun update(
		organizationId: UUID,
		packageId: UUID,
		name: String?,
		description: String?,
		priceMinor: Long?,
		maxQuantity: Int?,
		exclusive: Boolean?,
		placementStartDate: LocalDate?,
		placementEndDate: LocalDate?,
		currentUser: CurrentUser,
	): SponsorshipPackage {
		membershipService.requireManagerRole(organizationId, currentUser)
		val existing = findPackage(organizationId, packageId)
		validateMaxQuantity(maxQuantity)
		validateDateRange(placementStartDate ?: existing.placementStartDate, placementEndDate ?: existing.placementEndDate)
		sponsorshipPackageRepository.update(
			packageId, organizationId, name, description, priceMinor, maxQuantity, exclusive, placementStartDate, placementEndDate,
		)
		auditService.record(currentUser.userId, organizationId, "sponsorship_package.updated", "sponsorship_package", packageId)
		return findPackage(organizationId, packageId)
	}

	@Transactional
	fun publish(organizationId: UUID, packageId: UUID, currentUser: CurrentUser): SponsorshipPackage {
		membershipService.requireManagerRole(organizationId, currentUser)
		val sponsorshipPackage = findPackage(organizationId, packageId)
		if (sponsorshipPackage.status == SponsorshipPackageStatus.PUBLISHED) return sponsorshipPackage
		if (sponsorshipPackage.status == SponsorshipPackageStatus.ARCHIVED) {
			throw ValidationException("An archived sponsorship package cannot be published.")
		}
		sponsorshipPackageRepository.updateStatus(packageId, organizationId, SponsorshipPackageStatus.PUBLISHED)
		auditService.record(currentUser.userId, organizationId, "sponsorship_package.published", "sponsorship_package", packageId)
		return findPackage(organizationId, packageId)
	}

	@Transactional
	fun updateStatus(organizationId: UUID, packageId: UUID, status: SponsorshipPackageStatus, currentUser: CurrentUser): SponsorshipPackage {
		membershipService.requireManagerRole(organizationId, currentUser)
		findPackage(organizationId, packageId)
		sponsorshipPackageRepository.updateStatus(packageId, organizationId, status)
		auditService.record(currentUser.userId, organizationId, "sponsorship_package.status_updated", "sponsorship_package", packageId)
		return findPackage(organizationId, packageId)
	}

	/** Assigns/replaces a confirmed sponsor's logo — see this class's doc for why this is an admin action, not a public-checkout upload. */
	@Transactional
	fun assignSponsorLogo(organizationId: UUID, sponsorId: UUID, assetId: UUID, altText: String?, currentUser: CurrentUser): MediaAssignment {
		val sponsor = sponsorRepository.findById(sponsorId, organizationId)
			?: throw NotFoundException("SPONSOR_NOT_FOUND", "The sponsor could not be found.")
		// A sponsor's logo is shown on the public directory unconditionally once
		// assigned — there's no separate "is this sponsor active" flag to gate on this
		// slice (a sponsor row only exists at all once its sponsorship is at least
		// PENDING), so visibility here is simpler than a product's ACTIVE-status gate.
		return mediaAssignmentService.assign(organizationId, MediaEntityType.SPONSOR, sponsor.id, MediaUsageSlot.SPONSOR_LOGO, assetId, altText, currentUser) {
			Visibility.PUBLIC
		}
	}

	/** Sponsor-contact CRM read (Phase 6 remainder, ADR-019). */
	fun getSponsor(organizationId: UUID, sponsorId: UUID, currentUser: CurrentUser): Sponsor {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return sponsorRepository.findById(sponsorId, organizationId)
			?: throw NotFoundException("SPONSOR_NOT_FOUND", "The sponsor could not be found.")
	}

	/** Sponsor-contact CRM update (Phase 6 remainder, ADR-019) — widens `sponsor` beyond name/contact-email with a small, bounded field set (phone/company name/notes), not a full multi-contact CRM table. */
	@Transactional
	fun updateSponsor(
		organizationId: UUID,
		sponsorId: UUID,
		name: String?,
		contactEmail: String?,
		phone: String?,
		companyName: String?,
		notes: String?,
		currentUser: CurrentUser,
	): Sponsor {
		membershipService.requireManagerRole(organizationId, currentUser)
		sponsorRepository.findById(sponsorId, organizationId)
			?: throw NotFoundException("SPONSOR_NOT_FOUND", "The sponsor could not be found.")
		sponsorRepository.update(sponsorId, organizationId, name, contactEmail, phone, companyName, notes)
		auditService.record(currentUser.userId, organizationId, "sponsor.updated", "sponsor", sponsorId)
		return sponsorRepository.findById(sponsorId, organizationId)!!
	}

	/**
	 * A shareable QR code image + the plain URL it encodes (Phase 6 remainder,
	 * ADR-019) — for an org admin to hand to a prospective sponsor or print in event
	 * materials. Deliberately stateless: [url] is supplied by the caller (the frontend
	 * already knows its own public sponsorship-page route,
	 * `${origin}/sponsors/${organizationSlug}`), nothing is persisted, and there is no
	 * click-through tracking — this is a convenience image generator, not an analytics
	 * feature (see ADR-019's rationale for why no tracking table exists).
	 */
	fun buildShareLink(organizationId: UUID, url: String, currentUser: CurrentUser): String {
		membershipService.requireActiveMembership(organizationId, currentUser)
		if (url.length > 2000 || allowedShareLinkPrefixes.none { url.startsWith(it) }) {
			throw ValidationException("A valid http(s) URL is required to generate a QR code.")
		}
		return qrCodeGenerator.generatePngDataUri(url)
	}

	private fun findPackage(organizationId: UUID, packageId: UUID): SponsorshipPackage =
		sponsorshipPackageRepository.findById(packageId, organizationId)
			?: throw NotFoundException("SPONSORSHIP_PACKAGE_NOT_FOUND", "The sponsorship package could not be found.")

	private fun validateMaxQuantity(maxQuantity: Int?) {
		if (maxQuantity != null && maxQuantity <= 0) {
			throw ValidationException(
				"Max quantity must be greater than zero.",
				listOf(FieldError("maxQuantity", "Must be greater than zero.")),
			)
		}
	}

	private fun validateDateRange(placementStartDate: LocalDate?, placementEndDate: LocalDate?) {
		if (placementStartDate != null && placementEndDate != null && placementEndDate.isBefore(placementStartDate)) {
			throw ValidationException(
				"Placement end date must be on or after the placement start date.",
				listOf(FieldError("placementEndDate", "Must be on or after the placement start date.")),
			)
		}
	}
}

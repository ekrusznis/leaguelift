package com.leaguelift.membership.application

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.membership.persistence.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Owns organization-membership authorization checks. Every other module that needs to
 * answer "can this user act on this organization?" goes through this service rather
 * than querying organization_membership directly (DESIGN-DOC.md sections 7, 18.2).
 */
@Service
class MembershipService(private val membershipRepository: MembershipRepository) {

	@Transactional
	fun grantOwner(organizationId: UUID, userId: UUID): OrganizationMembership =
		membershipRepository.insert(organizationId, userId, MembershipRole.OWNER)

	@Transactional
	fun grantMembership(organizationId: UUID, userId: UUID, role: MembershipRole): OrganizationMembership {
		require(role != MembershipRole.OWNER) {
			"grantMembership must not be used to grant OWNER; ownership transfer is a separate controlled workflow."
		}
		if (membershipRepository.existsForUser(organizationId, userId)) {
			throw ValidationException("This user is already a member of the organization.")
		}
		return membershipRepository.insert(organizationId, userId, role)
	}

	/**
	 * Read-only organization reporting access. OWNER/ADMINISTRATOR may manage the
	 * underlying data; FINANCE_MANAGER and VIEWER may inspect reports but are not
	 * promoted to manager-level mutation rights. This mirrors the capability registry's
	 * `organization.report.view` grant instead of routing report reads through the
	 * broader manager check.
	 */
	fun requireReportingRole(organizationId: UUID, currentUser: CurrentUser): OrganizationMembership {
		val membership = requireActiveMembership(organizationId, currentUser)
		if (membership.role !in REPORTING_ROLES) {
			throw ForbiddenException(
				code = "ORGANIZATION_REPORT_ACCESS_DENIED",
				message = "You do not have permission to view organization reports.",
			)
		}
		return membership
	}

	fun hasManagerRole(organizationId: UUID, currentUser: CurrentUser): Boolean {
		if (currentUser.platformAdministrator) return true
		val membership = membershipRepository.findActiveMembership(organizationId, currentUser.userId) ?: return false
		return membership.role == MembershipRole.OWNER || membership.role == MembershipRole.ADMINISTRATOR
	}

	/** OWNER or ADMINISTRATOR — the roles allowed to invite/manage other members. */
	fun requireManagerRole(organizationId: UUID, currentUser: CurrentUser): OrganizationMembership {
		val membership = requireActiveMembership(organizationId, currentUser)
		if (membership.role != MembershipRole.OWNER && membership.role != MembershipRole.ADMINISTRATOR) {
			throw ForbiddenException(
				code = "MEMBERSHIP_MANAGEMENT_DENIED",
				message = "Only organization owners and administrators can manage members.",
			)
		}
		return membership
	}

	/**
	 * OWNER only — for the owner-only actions DESIGN-DOC.md section 10.2 calls out
	 * explicitly (payout onboarding, change payout account, transfer ownership,
	 * suspend/close org, high-risk financial settings). Platform admins still get the
	 * same synthetic-membership bypass as [requireActiveMembership]/[requireManagerRole].
	 */
	fun requireOwnerRole(organizationId: UUID, currentUser: CurrentUser): OrganizationMembership {
		val membership = requireActiveMembership(organizationId, currentUser)
		if (membership.role != MembershipRole.OWNER) {
			throw ForbiddenException(
				code = "OWNER_ACTION_DENIED",
				message = "Only the organization owner can perform this action.",
			)
		}
		return membership
	}

	@Transactional
	fun updateRole(organizationId: UUID, membershipId: UUID, newRole: MembershipRole, currentUser: CurrentUser) {
		requireManagerRole(organizationId, currentUser)
		val target = membershipRepository.findById(membershipId)
			?.takeIf { it.organizationId == organizationId }
			?: throw NotFoundException("MEMBERSHIP_NOT_FOUND", "The membership could not be found.")
		if (target.role == MembershipRole.OWNER || newRole == MembershipRole.OWNER) {
			throw ValidationException("Ownership cannot be changed through this endpoint.")
		}
		membershipRepository.updateRole(membershipId, newRole)
	}

	@Transactional
	fun revoke(organizationId: UUID, membershipId: UUID, currentUser: CurrentUser) {
		requireManagerRole(organizationId, currentUser)
		val target = membershipRepository.findById(membershipId)
			?.takeIf { it.organizationId == organizationId }
			?: throw NotFoundException("MEMBERSHIP_NOT_FOUND", "The membership could not be found.")
		if (target.role == MembershipRole.OWNER) {
			throw ValidationException("The organization owner's membership cannot be revoked.")
		}
		membershipRepository.revoke(membershipId)
	}

	fun requireActiveMembership(organizationId: UUID, currentUser: CurrentUser): OrganizationMembership {
		if (currentUser.platformAdministrator) {
			// Platform admins can act across organizations for support/operational
			// purposes (DESIGN-DOC.md section 7.1), but this still returns a
			// synthetic membership rather than silently skipping authorization
			// elsewhere in the call chain.
			return membershipRepository.findActiveMembership(organizationId, currentUser.userId)
				?: OrganizationMembership(
					id = java.util.UUID(0, 0),
					organizationId = organizationId,
					userId = currentUser.userId,
					role = MembershipRole.OWNER,
					status = com.leaguelift.membership.domain.MembershipStatus.ACTIVE,
					createdAt = java.time.Instant.EPOCH,
					updatedAt = java.time.Instant.EPOCH,
				)
		}
		return membershipRepository.findActiveMembership(organizationId, currentUser.userId)
			?: throw ForbiddenException(
				code = "ORGANIZATION_ACCESS_DENIED",
				message = "You do not have access to this organization.",
			)
	}

	fun listMembers(organizationId: UUID, offset: Int, limit: Int) =
		membershipRepository.listForOrganization(organizationId, offset, limit)

	fun countMembers(organizationId: UUID) = membershipRepository.countForOrganization(organizationId)

	private companion object {
		val REPORTING_ROLES = setOf(
			MembershipRole.OWNER,
			MembershipRole.ADMINISTRATOR,
			MembershipRole.FINANCE_MANAGER,
			MembershipRole.VIEWER,
		)
	}
}

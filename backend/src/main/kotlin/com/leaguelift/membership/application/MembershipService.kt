package com.leaguelift.membership.application

import com.leaguelift.common.error.ForbiddenException
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
}

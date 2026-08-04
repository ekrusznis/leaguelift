package com.rally26.organization.integration

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.membership.application.MembershipService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises DESIGN-DOC.md section 22.3 critical scenario 1 end-to-end against a real
 * Postgres instance: "User from Organization A cannot read Organization B", and its
 * corollary, the creating user CAN read their own organization.
 *
 * Requires Docker (Testcontainers). Not executed in the sandbox this scaffold was
 * generated in — run `./gradlew test` locally with Docker running.
 */
class OrganizationIsolationIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var membershipService: MembershipService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var auditService: AuditService

	@Test
	fun `a user cannot read an organization they are not a member of`() {
		val ownerAppUser = passwordAuthenticationService.register("owner-${System.nanoTime()}@example.com", "password1234", "Owner")
		val owner = passwordAuthenticationService.toCurrentUser(ownerAppUser)

		val outsiderAppUser = passwordAuthenticationService.register("outsider-${System.nanoTime()}@example.com", "password1234", "Outsider")
		val outsider = passwordAuthenticationService.toCurrentUser(outsiderAppUser)

		val organization = organizationService.create(
			"Riverside Soccer",
			"riverside-soccer-${System.nanoTime()}",
			OrganizationType.RECREATIONAL_LEAGUE,
			owner,
		)

		val readByOwner = organizationService.get(organization.id, owner)
		assertEquals(organization.id, readByOwner.id)

		assertFailsWith<ForbiddenException> {
			organizationService.get(organization.id, outsider)
		}
	}
}

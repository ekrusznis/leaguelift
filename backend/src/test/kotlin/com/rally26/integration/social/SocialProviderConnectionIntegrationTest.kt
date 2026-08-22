package com.rally26.integration.social

import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.common.error.ValidationException
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationReadiness
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Social Sharing & Connected Accounts, Slice 1 — real-Postgres proof that the 3 new
 * providers are correctly registered in the existing generic integration framework
 * and that the athlete-exclusion rule (brief §10) is enforced before any provider
 * adapter is ever reached.
 */
class SocialProviderConnectionIntegrationTest : AbstractIntegrationTest() {
    @Autowired lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired lateinit var organizationService: OrganizationService

    @Autowired lateinit var oauthService: IntegrationOAuthService

    @Autowired lateinit var catalogService: IntegrationCatalogService

    @Autowired lateinit var roleAssignmentRepository: RoleAssignmentRepository

    private fun registerUser(prefix: String) =
        passwordAuthenticationService.toCurrentUser(
            passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", prefix),
        )

    @Test
    fun `the catalog lists all 3 social providers as not-configured and user-scoped`() {
        val user = registerUser("catalogUser")

        val catalog = catalogService.listForUser(user)
        val socialEntries =
            catalog.filter {
                it.definition.provider in
                    setOf(IntegrationProvider.INSTAGRAM, IntegrationProvider.FACEBOOK, IntegrationProvider.X)
            }

        assertTrue(
            socialEntries.size == 3,
            "expected all 3 social providers in the personal catalog, found ${socialEntries.map { it.definition.provider }}",
        )
        socialEntries.forEach {
            assertTrue(
                it.readiness == IntegrationReadiness.NOT_CONFIGURED,
                "${it.definition.provider} should be NOT_CONFIGURED until real credentials exist",
            )
        }
    }

    @Test
    fun `a non-athlete user starting Instagram authorization fails closed on missing credentials, not on role`() {
        val user = registerUser("nonAthlete")

        // No INSTAGRAM_CLIENT_ID/etc. is configured in the test profile — this proves
        // the request reaches the real adapter/catalog readiness check (fails closed
        // for the right reason) rather than being rejected earlier for being an athlete.
        assertFailsWith<ServiceUnavailableException> {
            oauthService.startUserAuthorization(IntegrationProvider.INSTAGRAM, user)
        }
    }

    @Test
    fun `an athlete-only user is rejected before reaching the provider adapter at all`() {
        val owner = registerUser("athleteOrgOwner")
        val athlete = registerUser("athleteUser")
        val organization =
            organizationService.create(
                "Athlete Social Test Org",
                "athlete-social-test-${System.nanoTime()}",
                OrganizationType.TRAVEL_CLUB,
                owner,
            )
        roleAssignmentRepository.grant(
            organization.id,
            athlete.userId,
            RoleAssignmentContextType.PARTICIPANT,
            UUID.randomUUID(),
            ResourceRole.ATHLETE_SELF,
            owner.userId,
        )

        val error =
            assertFailsWith<ValidationException> {
                oauthService.startUserAuthorization(IntegrationProvider.INSTAGRAM, athlete)
            }
        assertTrue(error.message?.contains("Athlete", ignoreCase = false) == true)

        // A non-social provider stays reachable for the same athlete account — the
        // block is scoped to the 3 social providers, not a blanket integration ban.
        assertFailsWith<ServiceUnavailableException> {
            oauthService.startUserAuthorization(IntegrationProvider.GOOGLE_CALENDAR, athlete)
        }
    }
}

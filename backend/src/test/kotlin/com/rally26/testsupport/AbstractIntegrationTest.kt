package com.rally26.testsupport

import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

/**
 * Base class for tests that need a real PostgreSQL instance (repository/integration
 * tests, per DESIGN-DOC.md section 22.1). Requires a local Docker daemon — this is
 * not runnable in the sandboxed environment this scaffold was generated in; run it
 * locally or in CI where Docker is available.
 *
 * Uses the singleton container pattern: the container is started once for the entire
 * test suite (not per-class) and cleaned up by Testcontainers' Ryuk reaper at JVM
 * exit. Without this, when multiple test classes extend this base, the first class
 * to finish would stop the shared container and subsequent classes would see
 * ConnectException.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired
    private lateinit var organizationSubscriptionRepository: OrganizationSubscriptionRepository

    /**
     * An organization with no `organization_subscription` row resolves to STARTER
     * (deny-by-default, see `PlanEntitlementService`), which since Phase 46 no longer
     * has every entitlement a test fixture might assume (sponsorships, family credits,
     * unlimited campaigns, advanced reporting are Club+ only). Call this in any test
     * that exercises one of those Club-gated features but isn't itself testing plan
     * entitlements, so the test's premise (not the plan tier) is what's under test.
     */
    protected fun activateClubPlan(organizationId: UUID) {
        organizationSubscriptionRepository.insertActive(organizationId, "FOUNDING_CLUB")
    }

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("rally26_test")
                .withUsername("rally26_test")
                .withPassword("rally26_test")
                .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

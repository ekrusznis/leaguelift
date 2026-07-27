package com.leaguelift.testsupport

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

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

	companion object {
		@JvmStatic
		val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
			.withDatabaseName("leaguelift_test")
			.withUsername("leaguelift_test")
			.withPassword("leaguelift_test")
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

package com.leaguelift.testsupport

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for tests that need a real PostgreSQL instance (repository/integration
 * tests, per DESIGN-DOC.md section 22.1). Requires a local Docker daemon — this is
 * not runnable in the sandboxed environment this scaffold was generated in; run it
 * locally or in CI where Docker is available.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

	companion object {
		@Container
		@JvmStatic
		val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
			.withDatabaseName("leaguelift_test")
			.withUsername("leaguelift_test")
			.withPassword("leaguelift_test")

		@DynamicPropertySource
		@JvmStatic
		fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", postgres::getJdbcUrl)
			registry.add("spring.datasource.username", postgres::getUsername)
			registry.add("spring.datasource.password", postgres::getPassword)
		}
	}
}

// Version baseline per DESIGN-DOC.md section 11.2: Spring Boot 4.1.0, Kotlin 2.3.21,
// Gradle 9.6.1. Patch versions may be bumped when builds/tests pass; major version
// changes require an ADR (DESIGN-DOC.md section 11.2).
//
// Java target is 17, not the originally documented 21 — see ADR-013. JDK 21 wasn't
// reliably resolvable on the founder's dev machine even with the Foojay auto-download
// resolver; Spring Boot 4.1 and Kotlin 2.3 both fully support 17 (Spring Boot's
// minimum since 3.2), so this is a low-risk downgrade for local development.
plugins {
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
}

group = "com.leaguelift"
version = "0.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17) // was 21 — see ADR-013
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")

	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

	// S3-compatible object storage client (DigitalOcean Spaces prod/staging, MinIO
	// local/test — ADR-012). Pure Java, no Spring/Kotlin coupling; url-connection-client
	// is the lightest sync HTTP client, sufficient since the backend's own S3 traffic is
	// low-volume (presign + head/get during upload confirmation, no byte-proxying).
	implementation(platform("software.amazon.awssdk:bom:2.29.52"))
	implementation("software.amazon.awssdk:s3")
	implementation("software.amazon.awssdk:url-connection-client")

	// Stripe Connect Express onboarding scaffolding only (ADR-005) — no live charge
	// routing yet, gated behind Phase 5 per DESIGN-DOC.md section 16.
	implementation("com.stripe:stripe-java:29.0.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.13")
	testImplementation("com.ninja-squad:springmockk:4.0.2")
	testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:minio")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

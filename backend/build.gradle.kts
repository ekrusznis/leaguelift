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

group = "com.rally26"
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
	// Jackson 2.x Kotlin module — used only by the Jackson 2 `ObjectMapper` bean
	// (JacksonConfig.objectMapper()), which backs JSONB (de)serialization in repositories
	// like OrganizationRepository. Spring Boot 4.1's HTTP message conversion runs on
	// Jackson 3.x (`tools.jackson`) instead — see the second dependency below.
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	// Jackson 3.x Kotlin module (relocated coordinates: tools.jackson.module, not
	// com.fasterxml.jackson.module) — required so Spring Boot 4.1's auto-configured
	// tools.jackson.databind.json.JsonMapper (the one actually used by
	// AbstractJacksonHttpMessageConverter for request/response bodies) understands Kotlin
	// data class constructors and default parameter values. Without this, Jackson 3's
	// bean deserializer treats every constructor parameter as required, even ones with a
	// Kotlin default, and omitting them throws instead of falling back to the default.
	implementation("tools.jackson.module:jackson-module-kotlin:3.0.3")

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

	// QR code generation for the sponsorship share-link feature (Phase 6 remainder,
	// ADR-019) — DESIGN-DOC.md section 8.3 listed `qr_code_reference` as design-target
	// only; ZXing is the first real QR implementation in this codebase. `javase` brings
	// MatrixToImageWriter (bit matrix -> BufferedImage); no other ZXing modules needed.
	implementation("com.google.zxing:core:3.5.3")
	implementation("com.google.zxing:javase:3.5.3")

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

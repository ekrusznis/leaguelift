package com.rally26

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// @EnableScheduling added for SponsorshipRenewalReminderService (Phase 6 remainder,
// ADR-019) — the first @Scheduled job in this codebase. As of Phase 8 slice 1
// (ADR-022) it also drives the outbox worker's poll loop (OutboxWorker) and the
// sponsorship-renewal scan (SponsorshipRenewalScanner).
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class Rally26ApiApplication

fun main(args: Array<String>) {
    runApplication<Rally26ApiApplication>(*args)
}

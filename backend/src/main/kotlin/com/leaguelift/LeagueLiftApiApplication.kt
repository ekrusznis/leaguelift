package com.leaguelift

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling

// @EnableScheduling added for SponsorshipRenewalReminderService (Phase 6 remainder,
// ADR-019) — the first @Scheduled job in this codebase. Deliberately not the outbox
// worker (DESIGN-DOC.md section 17, still unconsumed/Phase 8) — see that service's doc.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class LeagueLiftApiApplication

fun main(args: Array<String>) {
	runApplication<LeagueLiftApiApplication>(*args)
}

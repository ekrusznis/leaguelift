package com.rally26.credit.application

import com.rally26.audit.application.AuditService
import com.rally26.credit.persistence.FamilyCreditGrantRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private val log = LoggerFactory.getLogger(FamilyCreditExpirationScanner::class.java)

/**
 * Flips PENDING/AVAILABLE family credit grants past their expiry to EXPIRED
 * (Phase 23) — mirrors `FeePaymentReminderScanner`/`SponsorshipRenewalScanner`'s
 * plain scan-and-mark `@Scheduled` shape exactly. Only relevant for
 * organizations that chose `EXPIRES` over `ROLLOVER`; a rollover org has no
 * `expires_at` set on its grants, so this scanner is a safe no-op for it.
 */
@Component
class FamilyCreditExpirationScanner(
    private val grantRepository: FamilyCreditGrantRepository,
    private val auditService: AuditService,
) {
    @Scheduled(cron = "\${rally26.credit.expiration.cron:0 30 8 * * *}")
    fun scanAndExpire() {
        val expiring = grantRepository.findExpiring(Instant.now())
        if (expiring.isEmpty()) return
        log.info("Expiring {} family credit grant(s)", expiring.size)
        expiring.forEach { grant ->
            if (grantRepository.markExpired(grant.id, grant.organizationId) > 0) {
                auditService.record(null, grant.organizationId, "family_credit.expired", "family_credit_grant", grant.id)
            }
        }
    }
}

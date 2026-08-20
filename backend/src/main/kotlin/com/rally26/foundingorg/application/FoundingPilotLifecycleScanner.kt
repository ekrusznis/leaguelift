package com.rally26.foundingorg.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.FoundingPilotLifecycleProperties
import com.rally26.foundingorg.domain.FOUNDING_PILOT_REMINDER_SCHEDULE
import com.rally26.foundingorg.domain.FoundingOrgPromoCode
import com.rally26.foundingorg.persistence.FoundingOrgPromoCodeRepository
import com.rally26.identity.persistence.AppUserRepository
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.onboarding.owner.persistence.OwnerOnboardingRepository
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.subscription.persistence.OrganizationSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class FoundingPilotEmailPayload(
    val organizationName: String,
    val ownerEmails: List<String>,
    val daysRemaining: Long,
    val pilotEndDate: String,
)

private val log = LoggerFactory.getLogger(FoundingPilotLifecycleScanner::class.java)

/**
 * Founding Organization pilot (founder-directed, 2026-08-20): check-in emails at day
 * 30/60, weekly countdown warnings at day 63/70/77/84/89, then either the pilot converts
 * (a real Stripe subscription already exists — see [com.rally26.subscription.application.OrganizationSubscriptionService.handleSubscriptionChanged]'s
 * `convertingFoundingPromo` branch, which is the normal path) or expires at day 90 with a
 * real suspend (`MembershipService.requireActiveMembership`'s SUSPENDED enforcement is what
 * actually locks the org out — this scanner only flips the status).
 *
 * Modeled directly on `sponsorship/application/SponsorshipRenewalScanner.kt`: mark-then-
 * enqueue, not wrapped in the scan's own transaction, same accepted-risk rationale (a
 * pilot stuck retrying through outbox backoff would otherwise be re-enqueued the next day).
 */
@Component
class FoundingPilotLifecycleScanner(
    private val repository: FoundingOrgPromoCodeRepository,
    private val organizationRepository: OrganizationRepository,
    private val subscriptionRepository: OrganizationSubscriptionRepository,
    private val membershipRepository: MembershipRepository,
    private val appUserRepository: AppUserRepository,
    private val ownerOnboardingRepository: OwnerOnboardingRepository,
    private val outboxWriter: OutboxWriter,
    private val properties: FoundingPilotLifecycleProperties,
    private val objectMapper: ObjectMapper,
) {
    @Scheduled(cron = "\${rally26.foundingorg.lifecycle.cron:0 0 8 * * *}")
    fun scanAndEnqueue() {
        if (!properties.enabled) return
        val activePilots = repository.listActivePilots()
        if (activePilots.isEmpty()) return
        activePilots.forEach(::scanOne)
    }

    private fun scanOne(pilot: FoundingOrgPromoCode) {
        val redeemedAt = pilot.redeemedAt ?: return
        val organizationId = pilot.organizationId ?: return
        val daysSinceStart = Duration.between(redeemedAt, Instant.now()).toDays()

        var index = pilot.nextReminderIndex
        while (index < FOUNDING_PILOT_REMINDER_SCHEDULE.size && FOUNDING_PILOT_REMINDER_SCHEDULE[index].first <= daysSinceStart) {
            enqueueEmail(pilot, organizationId, FOUNDING_PILOT_REMINDER_SCHEDULE[index].second)
            index++
        }
        if (index != pilot.nextReminderIndex) {
            repository.advanceReminderIndex(pilot.id, index)
        }

        val pilotEndsAt = pilot.pilotEndsAt ?: return
        if (Instant.now() < pilotEndsAt) return
        expirePilot(pilot, organizationId)
    }

    @Transactional
    fun expirePilot(
        pilot: FoundingOrgPromoCode,
        organizationId: UUID,
    ) {
        val subscription = subscriptionRepository.findByOrganizationId(organizationId)
        if (subscription?.stripeSubscriptionId != null) {
            // Converted to real billing since the last scan but the webhook path didn't
            // catch it (defensive — the normal path is handleSubscriptionChanged's own
            // markConverted call, which fires the moment Stripe confirms the checkout).
            repository.markConverted(organizationId)
            return
        }
        organizationRepository.findById(organizationId) ?: return
        ownerOnboardingRepository.suspendActivatedOrganization(organizationId)
        repository.markExpired(organizationId)
        log.info("Founding Organization pilot expired for organization {}, suspending access", organizationId)
        enqueueEmail(pilot, organizationId, "founding_org.pilot_expired")
    }

    private fun enqueueEmail(
        pilot: FoundingOrgPromoCode,
        organizationId: UUID,
        eventType: String,
    ) {
        val organization = organizationRepository.findById(organizationId) ?: return
        val ownerEmails =
            membershipRepository
                .listActiveManagers(organizationId)
                .mapNotNull { appUserRepository.findById(it.userId)?.email }
        if (ownerEmails.isEmpty()) return
        val pilotEndsAt = pilot.pilotEndsAt ?: return
        val daysRemaining = Duration.between(Instant.now(), pilotEndsAt).toDays().coerceAtLeast(0)
        val payload =
            FoundingPilotEmailPayload(
                organizationName = organization.name,
                ownerEmails = ownerEmails,
                daysRemaining = daysRemaining,
                pilotEndDate = pilotEndsAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
            )
        outboxWriter.write(
            aggregateType = "founding_org_promo_code",
            aggregateId = pilot.id,
            organizationId = organizationId,
            eventType = eventType,
            payloadJson = objectMapper.writeValueAsString(payload),
        )
    }
}

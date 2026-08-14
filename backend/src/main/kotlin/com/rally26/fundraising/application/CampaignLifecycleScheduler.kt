package com.rally26.fundraising.application

import com.rally26.audit.application.AuditService
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.persistence.CampaignRepository
import com.rally26.fundraisinggame.domain.FundraisingGameStatus
import com.rally26.fundraisinggame.persistence.FundraisingGameRepository
import com.rally26.outbox.application.OutboxWriter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Advances date-driven fundraiser lifecycle states.
 *
 * Campaign dates are day-granularity today, so an ACTIVE campaign remains open through
 * its configured end date and transitions to ENDED on the first scan after that date.
 * CLOSED remains an explicit owner closeout action rather than an automatic transition.
 */
@Component
class CampaignLifecycleScheduler(
    private val campaignRepository: CampaignRepository,
    private val fundraisingGameRepository: FundraisingGameRepository,
    private val auditService: AuditService,
    private val clock: Clock,
    private val outboxWriter: OutboxWriter? = null,
) {
    @Scheduled(cron = "\${rally26.fundraising.lifecycle-cron:0 5 * * * *}")
    @Transactional
    fun advanceLifecycle() {
        val today = LocalDate.now(clock)
        activateScheduledCampaigns(today)
        endExpiredCampaigns(today)
    }

    private fun activateScheduledCampaigns(today: LocalDate) {
        campaignRepository.findScheduledDue(today).forEach { campaign ->
            if (campaignRepository.activateScheduled(campaign.id, campaign.organizationId) == 0) return@forEach
            auditService.record(
                actorUserId = null,
                organizationId = campaign.organizationId,
                action = "campaign.activated",
                entityType = "campaign",
                entityId = campaign.id,
                metadataJson = """{"statusBefore":"SCHEDULED","statusAfter":"ACTIVE","automatic":true}""",
                teamId = campaign.teamId,
                summary = "Scheduled fundraiser activated",
            )
            enqueueNotification("fundraiser.activated", campaign)
        }
    }

    private fun endExpiredCampaigns(today: LocalDate) {
        campaignRepository.findActiveReadyToEnd(today).forEach { campaign ->
            if (campaignRepository.markEnded(campaign.id, campaign.organizationId) == 0) return@forEach
            closeAttachedFreeGame(campaign)
            auditService.record(
                actorUserId = null,
                organizationId = campaign.organizationId,
                action = "campaign.ended",
                entityType = "campaign",
                entityId = campaign.id,
                metadataJson = """{"statusBefore":"ACTIVE","statusAfter":"ENDED","automatic":true}""",
                teamId = campaign.teamId,
                summary = "Fundraiser reached its end date",
            )
            enqueueNotification("fundraiser.ended", campaign)
        }
    }

    private fun enqueueNotification(
        eventType: String,
        campaign: Campaign,
    ) {
        outboxWriter?.write(
            aggregateType = "campaign",
            aggregateId = campaign.id,
            organizationId = campaign.organizationId,
            eventType = eventType,
            payloadJson = """{"campaignId":"${campaign.id}"}""",
        )
    }

    private fun closeAttachedFreeGame(campaign: Campaign) {
        val game = fundraisingGameRepository.findByCampaign(campaign.id) ?: return
        if (game.status != FundraisingGameStatus.OPEN) return
        if (fundraisingGameRepository.updateStatus(game.id, campaign.organizationId, FundraisingGameStatus.CLOSED) == 0) return
        auditService.record(
            actorUserId = null,
            organizationId = campaign.organizationId,
            action = "fundraising_game.closed",
            entityType = "fundraising_game",
            entityId = game.id,
            metadataJson = """{"reason":"campaign_ended","automatic":true}""",
            teamId = campaign.teamId,
            summary = "Free fundraising game closed when fundraiser ended",
        )
    }
}

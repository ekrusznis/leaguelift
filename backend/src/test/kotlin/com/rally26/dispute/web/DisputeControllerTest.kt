package com.rally26.dispute.web

import com.rally26.common.web.CurrentUser
import com.rally26.dispute.application.DisputeService
import com.rally26.dispute.domain.DisputeSourceType
import com.rally26.dispute.domain.DisputeStatus
import com.rally26.dispute.domain.PaymentDispute
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DisputeControllerTest {
    private val disputeService = mockk<DisputeService>()
    private val controller = DisputeController(disputeService)
    private val organizationId = UUID.randomUUID()
    private val currentUser = mockk<CurrentUser>()

    @Test
    fun `list delegates manager-role enforcement to the service and maps rows to responses`() {
        val dispute =
            PaymentDispute(
                id = UUID.randomUUID(),
                organizationId = organizationId,
                sourceType = DisputeSourceType.ORDER,
                sourceId = UUID.randomUUID(),
                stripeDisputeId = "dp_test_1",
                stripeChargeId = "ch_test_1",
                amountMinor = 5_000L,
                currency = "usd",
                reason = "fraudulent",
                status = DisputeStatus.NEEDS_RESPONSE,
                evidenceDueBy = Instant.now(),
                openedAt = Instant.now(),
                resolvedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { disputeService.list(organizationId, currentUser) } returns listOf(dispute)

        val response = controller.list(organizationId, currentUser)

        assertEquals(1, response.size)
        assertEquals(dispute.id, response[0].id)
        assertEquals(DisputeStatus.NEEDS_RESPONSE, response[0].status)
    }
}

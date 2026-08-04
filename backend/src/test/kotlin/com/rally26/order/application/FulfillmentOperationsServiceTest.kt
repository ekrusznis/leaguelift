package com.rally26.order.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentHistory
import com.rally26.order.domain.FulfillmentReprint
import com.rally26.order.domain.FulfillmentReprintStatus
import com.rally26.order.domain.FulfillmentSource
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderStatus
import com.rally26.order.persistence.FulfillmentHistoryRepository
import com.rally26.order.persistence.FulfillmentReprintRepository
import com.rally26.order.persistence.FulfillmentRepository
import com.rally26.order.persistence.OrderRepository
import com.rally26.store.application.ManualVendorService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FulfillmentOperationsServiceTest {
	private val orderRepository = mockk<OrderRepository>()
	private val fulfillmentRepository = mockk<FulfillmentRepository>()
	private val historyRepository = mockk<FulfillmentHistoryRepository>()
	private val reprintRepository = mockk<FulfillmentReprintRepository>()
	private val manualVendorService = mockk<ManualVendorService>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val service = FulfillmentOperationsService(
		orderRepository, fulfillmentRepository, historyRepository, reprintRepository,
		manualVendorService, membershipService, auditService,
	)
	private val orgId = UUID.randomUUID()
	private val user = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")
	private val orderId = UUID.randomUUID()

	@Test
	fun `update records append-only history for an allowed status transition`() {
		val existing = fulfillment(FulfillmentSource.MANUAL, FulfillmentStatus.READY)
		val updated = existing.copy(status = FulfillmentStatus.IN_PRODUCTION, statusChangedAt = Instant.now())
		stubManagerAndOrder()
		every { fulfillmentRepository.findByOrder(orderId) } returnsMany listOf(existing, updated)
		every { fulfillmentRepository.updateOperationalState(existing.id, FulfillmentStatus.IN_PRODUCTION, null, "LOCAL-12", null, null, null, null, null) } returns 1
		every {
			historyRepository.insert(orgId, existing.id, FulfillmentStatus.READY, FulfillmentStatus.IN_PRODUCTION, "Vendor accepted order.", user.userId)
		} returns FulfillmentHistory(UUID.randomUUID(), orgId, existing.id, FulfillmentStatus.READY, FulfillmentStatus.IN_PRODUCTION, "Vendor accepted order.", user.userId, Instant.now())
		every { auditService.record(user.userId, orgId, "fulfillment.updated", "fulfillment", existing.id) } just runs

		val result = service.update(
			orgId, orderId, FulfillmentStatus.IN_PRODUCTION, null, "LOCAL-12", null, null, null,
			null, null, "Vendor accepted order.", user,
		)

		assertEquals(FulfillmentStatus.IN_PRODUCTION, result.status)
		verify(exactly = 1) {
			historyRepository.insert(orgId, existing.id, FulfillmentStatus.READY, FulfillmentStatus.IN_PRODUCTION, "Vendor accepted order.", user.userId)
		}
	}

	@Test
	fun `update requires an attention reason`() {
		val existing = fulfillment(FulfillmentSource.MANUAL, FulfillmentStatus.READY)
		stubManagerAndOrder()
		every { fulfillmentRepository.findByOrder(orderId) } returns existing

		assertFailsWith<ValidationException> {
			service.update(
				orgId, orderId, FulfillmentStatus.NEEDS_ATTENTION, null, null, null, null, null,
				null, null, "Vendor issue.", user,
			)
		}
		verify(exactly = 0) { fulfillmentRepository.updateOperationalState(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Printify fulfillment cannot be assigned a manual vendor`() {
		val existing = fulfillment(FulfillmentSource.PRINTIFY, FulfillmentStatus.DRAFT_CREATED)
		stubManagerAndOrder()
		every { fulfillmentRepository.findByOrder(orderId) } returns existing

		assertFailsWith<ValidationException> {
			service.update(
				orgId, orderId, FulfillmentStatus.IN_PRODUCTION, UUID.randomUUID(), null, null, null, null,
				null, null, "Printify moved to production.", user,
			)
		}
	}

	@Test
	fun `requestReprint refuses a second open replacement`() {
		val existing = fulfillment(FulfillmentSource.MANUAL, FulfillmentStatus.DELIVERED)
		val open = reprint(existing.id, FulfillmentReprintStatus.REQUESTED)
		stubManagerAndOrder()
		every { fulfillmentRepository.findByOrder(orderId) } returns existing
		every { reprintRepository.findOpenByFulfillment(existing.id) } returns open

		assertFailsWith<ValidationException> {
			service.requestReprint(orgId, orderId, "Wrong size delivered", null, null, user)
		}
		verify(exactly = 0) { reprintRepository.insert(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `reprint cannot move backward from shipped to production`() {
		val fulfillment = fulfillment(FulfillmentSource.MANUAL, FulfillmentStatus.DELIVERED)
		val shipped = reprint(fulfillment.id, FulfillmentReprintStatus.SHIPPED)
		stubManagerAndOrder()
		every { reprintRepository.findById(shipped.id, orgId) } returns shipped

		assertFailsWith<ValidationException> {
			service.updateReprint(orgId, orderId, shipped.id, FulfillmentReprintStatus.IN_PRODUCTION, null, null, null, null, null, user)
		}
	}

	private fun stubManagerAndOrder() {
		every { membershipService.requireManagerRole(orgId, user) } returns OrganizationMembership(
			UUID.randomUUID(), orgId, user.userId, MembershipRole.ADMINISTRATOR, MembershipStatus.ACTIVE, Instant.now(), Instant.now(),
		)
		every { orderRepository.findById(orderId, orgId) } returns Order(
			orderId, orgId, UUID.randomUUID(), OrderStatus.CONFIRMED, "USD", "Buyer", "buyer@example.com", null,
			"cs_123", "pi_123", Instant.now(), null, Instant.now(),
		)
	}

	private fun fulfillment(source: FulfillmentSource, status: FulfillmentStatus) = Fulfillment(
		UUID.randomUUID(), orderId, source, status, null, null, null, null, null, null, null, null, null, null,
		Instant.now(), null, null, Instant.now(), Instant.now(),
	)

	private fun reprint(fulfillmentId: UUID, status: FulfillmentReprintStatus) = FulfillmentReprint(
		UUID.randomUUID(), orgId, fulfillmentId, orderId, status, "Wrong size", null, null, null, null, null,
		user.userId, Instant.now(), Instant.now(), null, null,
	)
}

package com.rally26.reporting.application

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.fee.domain.FeeAssignment
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.OrganizationFeeFinancialSummary
import com.rally26.fee.persistence.FeeAdjustmentRepository
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fee.persistence.FeeRepository
import com.rally26.household.domain.Household
import com.rally26.household.domain.HouseholdStatus
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.outbox.persistence.OutboxEventRepository
import com.rally26.webhook.domain.WebhookProcessingStatus
import com.rally26.webhook.persistence.WebhookEventRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.reporting.persistence.FeeCollectionRow
import com.rally26.reporting.persistence.ReportingRepository
import com.rally26.reporting.persistence.SourceTypeRevenueRow
import com.rally26.reporting.persistence.TeamRevenueRow
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReportingServiceTest {

	private val reportingRepository = mockk<ReportingRepository>()
	private val feeRepository = mockk<FeeRepository>()
	private val feePaymentRepository = mockk<FeePaymentRepository>()
	private val feeAdjustmentRepository = mockk<FeeAdjustmentRepository>()
	private val householdRepository = mockk<HouseholdRepository>()
	private val organizationRepository = mockk<OrganizationRepository>()
	private val webhookEventRepository = mockk<WebhookEventRepository>()
	private val outboxEventRepository = mockk<OutboxEventRepository>()
	private val membershipService = mockk<MembershipService>()
	private val authorizationService = mockk<AuthorizationService>()
	private val service = ReportingService(
		reportingRepository, feeRepository, feePaymentRepository, feeAdjustmentRepository, householdRepository,
		organizationRepository, webhookEventRepository, outboxEventRepository, membershipService, authorizationService,
	)

	private val orgId = UUID.randomUUID()
	private val manager = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

	private fun managerMembership() = OrganizationMembership(
		UUID.randomUUID(), orgId, manager.userId, MembershipRole.ADMINISTRATOR, MembershipStatus.ACTIVE, Instant.now(), Instant.now(),
	)

	@Test
	fun `getRevenueReport requires reporting role`() {
		every { membershipService.requireReportingRole(orgId, manager) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> { service.getRevenueReport(orgId, null, null, manager) }
	}

	@Test
	fun `getRevenueReport defaults to a trailing 30-day range when no dates are given`() {
		every { membershipService.requireReportingRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(SourceTypeRevenueRow("CONTRIBUTION", 5_000L))
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns emptyList()

		val report = service.getRevenueReport(orgId, null, null, manager)

		assertEquals(LocalDate.now(), report.to)
		assertEquals(LocalDate.now().minusDays(30), report.from)
		assertEquals(5_000L, report.totalMinor)
	}

	@Test
	fun `getRevenueReport totalMinor sums across every source type`() {
		every { membershipService.requireReportingRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(
			SourceTypeRevenueRow("CONTRIBUTION", 3_000L), SourceTypeRevenueRow("ORDER", 2_000L),
		)
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns listOf(TeamRevenueRow(UUID.randomUUID(), "Varsity Soccer", 1_500L))

		val report = service.getRevenueReport(orgId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), manager)

		assertEquals(5_000L, report.totalMinor)
	}

	@Test
	fun `exportRevenueReportCsv includes a row per source type and per team`() {
		every { membershipService.requireReportingRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(SourceTypeRevenueRow("CONTRIBUTION", 10_000L))
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns listOf(TeamRevenueRow(null, null, 10_000L))

		val csv = service.exportRevenueReportCsv(orgId, null, null, manager)

		assertTrue(csv.contains("CONTRIBUTION"))
		assertTrue(csv.contains("100.00"))
		assertTrue(csv.contains("Organization-wide"))
	}

	@Test
	fun `getFeeCollectionsReport combines in-range payments with the point-in-time outstanding balance`() {
		every { membershipService.requireReportingRole(orgId, manager) } returns managerMembership()
		val household = UUID.randomUUID()
		every { reportingRepository.feeCollections(orgId, any(), any()) } returns listOf(
			FeeCollectionRow(UUID.randomUUID(), household, "Smith Family", 2_500L, LocalDate.now()),
		)
		every { feeRepository.getFinancialSummary(orgId) } returns OrganizationFeeFinancialSummary(10_000L, 2_500L, 7_500L)

		val report = service.getFeeCollectionsReport(orgId, null, null, manager)

		assertEquals(2_500L, report.collectedMinor)
		assertEquals(7_500L, report.outstandingMinor)
	}

	@Test
	fun `getHouseholdFeeReport requires active membership, not just any caller`() {
		val householdId = UUID.randomUUID()
		every { membershipService.requireActiveMembership(orgId, manager) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> { service.getHouseholdFeeReport(orgId, householdId, null, null, manager) }
	}

	@Test
	fun `getHouseholdFeeReport throws NotFoundException for an unknown household`() {
		val householdId = UUID.randomUUID()
		every { membershipService.requireActiveMembership(orgId, manager) } returns managerMembership()
		every { householdRepository.findById(householdId, orgId) } returns null

		assertFailsWith<NotFoundException> { service.getHouseholdFeeReport(orgId, householdId, null, null, manager) }
	}

	@Test
	fun `getHouseholdFeeReport computes outstanding balance from only OPEN or PARTIALLY_PAID assignments`() {
		val householdId = UUID.randomUUID()
		val household = Household(householdId, orgId, "Smith Family", null, null, null, false, false, HouseholdStatus.ACTIVE, Instant.now(), Instant.now())
		every { membershipService.requireActiveMembership(orgId, manager) } returns managerMembership()
		every { householdRepository.findById(householdId, orgId) } returns household
		every { reportingRepository.feeCollections(orgId, any(), any(), householdId) } returns emptyList()

		val openAssignment = FeeAssignment(
			UUID.randomUUID(), orgId, householdId, null, null, "Fall dues", 10_000L, "USD", null, FeeAssignmentStatus.OPEN, Instant.now(), Instant.now(),
		)
		val paidAssignment = FeeAssignment(
			UUID.randomUUID(), orgId, householdId, null, null, "Uniform fee", 5_000L, "USD", null, FeeAssignmentStatus.PAID, Instant.now(), Instant.now(),
		)
		every { feeRepository.findByHousehold(householdId, orgId, 0, any()) } returns listOf(openAssignment, paidAssignment)
		every { feePaymentRepository.sumActiveByAssignment(openAssignment.id, orgId) } returns 4_000L
		every { feeAdjustmentRepository.sumActiveByAssignment(openAssignment.id, orgId) } returns 0L
		// paidAssignment is excluded by status, so no stub is needed for its sums.

		val report = service.getHouseholdFeeReport(orgId, householdId, null, null, manager)

		assertEquals(6_000L, report.outstandingMinor)
	}

	@Test
	fun `getPlatformReport requires the platform audit capability`() {
		every { authorizationService.requirePlatformCapability(manager, Capabilities.PLATFORM_AUDIT_VIEW) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> { service.getPlatformReport(null, null, manager) }
	}

	@Test
	fun `getPlatformReport refundRatePercent is null (not zero) when there was no GTV in range`() {
		every { authorizationService.requirePlatformCapability(manager, Capabilities.PLATFORM_AUDIT_VIEW) } returns Unit
		every { reportingRepository.countNewOrganizations(any(), any()) } returns 0L
		every { reportingRepository.countNewCustomers(any(), any()) } returns 0L
		every { reportingRepository.platformGrossTransactionVolume(any(), any()) } returns 0L
		every { reportingRepository.platformRefundedAmount(any(), any()) } returns 0L
		every { organizationRepository.countAllForPlatformAdmin() } returns 12L
		every { webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED) } returns 100L
		every { webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED) } returns 0L
		every { outboxEventRepository.countByStatus("PENDING") } returns 0L
		every { outboxEventRepository.countByStatus("DEAD_LETTER") } returns 0L

		val report = service.getPlatformReport(null, null, manager)

		assertEquals(null, report.refundRatePercent)
		assertEquals(12L, report.activeOrganizations)
	}

	@Test
	fun `getPlatformReport computes refundRatePercent as a percentage of GTV`() {
		every { authorizationService.requirePlatformCapability(manager, Capabilities.PLATFORM_AUDIT_VIEW) } returns Unit
		every { reportingRepository.countNewOrganizations(any(), any()) } returns 2L
		every { reportingRepository.countNewCustomers(any(), any()) } returns 5L
		every { reportingRepository.platformGrossTransactionVolume(any(), any()) } returns 100_000L
		every { reportingRepository.platformRefundedAmount(any(), any()) } returns 5_000L
		every { organizationRepository.countAllForPlatformAdmin() } returns 12L
		every { webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED) } returns 100L
		every { webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED) } returns 0L
		every { outboxEventRepository.countByStatus("PENDING") } returns 0L
		every { outboxEventRepository.countByStatus("DEAD_LETTER") } returns 0L

		val report = service.getPlatformReport(null, null, manager)

		assertEquals(5.0, report.refundRatePercent)
	}
}

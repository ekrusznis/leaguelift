package com.leaguelift.reporting.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.domain.OrganizationFeeFinancialSummary
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.household.domain.Household
import com.leaguelift.household.domain.HouseholdStatus
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.outbox.persistence.OutboxEventRepository
import com.leaguelift.webhook.domain.WebhookProcessingStatus
import com.leaguelift.webhook.persistence.WebhookEventRepository
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.reporting.persistence.FeeCollectionRow
import com.leaguelift.reporting.persistence.ReportingRepository
import com.leaguelift.reporting.persistence.SourceTypeRevenueRow
import com.leaguelift.reporting.persistence.TeamRevenueRow
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
	fun `getRevenueReport requires manager role`() {
		every { membershipService.requireManagerRole(orgId, manager) } throws ForbiddenException("DENIED", "no")

		assertFailsWith<ForbiddenException> { service.getRevenueReport(orgId, null, null, manager) }
	}

	@Test
	fun `getRevenueReport defaults to a trailing 30-day range when no dates are given`() {
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(SourceTypeRevenueRow("CONTRIBUTION", 5_000L))
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns emptyList()

		val report = service.getRevenueReport(orgId, null, null, manager)

		assertEquals(LocalDate.now(), report.to)
		assertEquals(LocalDate.now().minusDays(30), report.from)
		assertEquals(5_000L, report.totalMinor)
	}

	@Test
	fun `getRevenueReport totalMinor sums across every source type`() {
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(
			SourceTypeRevenueRow("CONTRIBUTION", 3_000L), SourceTypeRevenueRow("ORDER", 2_000L),
		)
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns listOf(TeamRevenueRow(UUID.randomUUID(), "Varsity Soccer", 1_500L))

		val report = service.getRevenueReport(orgId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), manager)

		assertEquals(5_000L, report.totalMinor)
	}

	@Test
	fun `exportRevenueReportCsv includes a row per source type and per team`() {
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership()
		every { reportingRepository.revenueBySourceType(orgId, any(), any()) } returns listOf(SourceTypeRevenueRow("CONTRIBUTION", 10_000L))
		every { reportingRepository.revenueByTeam(orgId, any(), any()) } returns listOf(TeamRevenueRow(null, null, 10_000L))

		val csv = service.exportRevenueReportCsv(orgId, null, null, manager)

		assertTrue(csv.contains("CONTRIBUTION"))
		assertTrue(csv.contains("100.00"))
		assertTrue(csv.contains("Organization-wide"))
	}

	@Test
	fun `getFeeCollectionsReport combines in-range payments with the point-in-time outstanding balance`() {
		every { membershipService.requireManagerRole(orgId, manager) } returns managerMembership()
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

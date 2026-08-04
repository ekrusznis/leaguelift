package com.rally26.fee.integration

import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.fee.application.FeeService
import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.PaymentMethod
import com.rally26.household.application.HouseholdService
import com.rally26.household.domain.Household
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Exercises manual payment/adjustment recording end to end against real Postgres:
 * balance/status auto-transitions on record and void, and org isolation (DESIGN-DOC.md
 * section 22.3 critical scenario 1) for the new payment/adjustment/collections
 * endpoints.
 */
class FeePaymentIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var organizationService: OrganizationService

	@Autowired
	lateinit var householdService: HouseholdService

	@Autowired
	lateinit var passwordAuthenticationService: PasswordAuthenticationService

	@Autowired
	lateinit var feeService: FeeService

	@Test
	fun `recording and voiding payments and adjustments transitions status correctly`() {
		val owner = registerUser("fee-payment-owner")
		val organization = createOrganization(owner)
		val household = createHousehold(organization, owner)

		val assignment = feeService.createAssignment(
			organization.id, household.id, null, null, "Spring Registration", 15000L, "USD", LocalDate.of(2026, 3, 1), owner,
		)
		assertEquals(FeeAssignmentStatus.OPEN, assignment.assignment.status)
		assertEquals(15000L, assignment.balance.balanceMinor)

		val afterFirstPayment = feeService.recordPayment(
			organization.id, assignment.assignment.id, 5000L, PaymentMethod.CASH, LocalDate.of(2026, 1, 15), "First installment", owner,
		)
		assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, afterFirstPayment.assignment.status)
		assertEquals(10000L, afterFirstPayment.balance.balanceMinor)

		val afterDiscount = feeService.applyAdjustment(
			organization.id, assignment.assignment.id, AdjustmentType.DISCOUNT, 2000L, "Sibling discount", owner,
		)
		assertEquals(8000L, afterDiscount.balance.balanceMinor)
		assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, afterDiscount.assignment.status)

		val secondPayment = feeService.recordPayment(
			organization.id, assignment.assignment.id, 8000L, PaymentMethod.CHECK, LocalDate.of(2026, 2, 1), null, owner,
		)
		assertEquals(0L, secondPayment.balance.balanceMinor)
		assertEquals(FeeAssignmentStatus.PAID, secondPayment.assignment.status)

		val payments = feeService.listPayments(organization.id, assignment.assignment.id, owner)
		val firstPaymentId = payments.first { it.amountMinor == 5000L }.id

		val afterVoid = feeService.voidPayment(organization.id, assignment.assignment.id, firstPaymentId, "Entered in error", owner)
		assertEquals(5000L, afterVoid.balance.balanceMinor, "voiding the first payment should restore its amount to the balance")
		assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, afterVoid.assignment.status, "no longer fully covered once the payment is voided")
	}

	@Test
	fun `an outsider cannot record payments or list an organization's collections`() {
		val owner = registerUser("fee-payment-org-owner")
		val organization = createOrganization(owner)
		val household = createHousehold(organization, owner)
		val assignment = feeService.createAssignment(
			organization.id, household.id, null, null, "Fall Dues", 10000L, "USD", null, owner,
		)

		val outsider = registerUser("fee-payment-outsider")

		assertFailsWith<ForbiddenException> {
			feeService.recordPayment(organization.id, assignment.assignment.id, 1000L, PaymentMethod.CASH, LocalDate.now(), null, outsider)
		}
		assertFailsWith<ForbiddenException> {
			feeService.listForOrganization(organization.id, null, false, outsider, 0, 20)
		}
	}

	private fun registerUser(prefix: String): CurrentUser {
		val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
		return passwordAuthenticationService.toCurrentUser(appUser)
	}

	private fun createOrganization(owner: CurrentUser): Organization =
		organizationService.create("Riverside Soccer", "riverside-soccer-fee-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner)

	private fun createHousehold(organization: Organization, owner: CurrentUser): Household =
		householdService.create(organization.id, "The Testerson Family", "family@example.com", null, null, owner)
}

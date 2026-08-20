package com.rally26.fee.integration

import com.rally26.actioncenter.persistence.ActionCenterRepository
import com.rally26.common.web.CurrentUser
import com.rally26.fee.application.FeeService
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.PaymentMethod
import com.rally26.fee.persistence.FeePaymentRepository
import com.rally26.fee.persistence.FeeRepository
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

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-030: every org-wide/reporting fee-balance
 * query summed ALL `fee_payment` rows regardless of `status`, so a household's
 * abandoned/never-completed Stripe checkout (status `PENDING_CHECKOUT`) counted as real
 * collected revenue and silently understated (or zeroed out) the true outstanding
 * balance. Found live on the Collections page: a $75 fee showed "$110.00 Paid" and
 * "$0.00 Balance" because of two leftover `PENDING_CHECKOUT` rows from a household that
 * never finished checkout. `FeePaymentRepository.sumActiveByAssignment` already had the
 * correct `status = 'CONFIRMED'` filter (with a doc comment explaining exactly why) —
 * every other site in the codebase computing a paid/collected total independently of
 * that method had drifted from it and needed the same fix.
 */
class FeePaymentPendingCheckoutIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var householdService: HouseholdService

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var feeService: FeeService

    @Autowired
    lateinit var feeRepository: FeeRepository

    @Autowired
    lateinit var feePaymentRepository: FeePaymentRepository

    @Autowired
    lateinit var actionCenterRepository: ActionCenterRepository

    @Test
    fun `a pending, never-confirmed online checkout does not count as collected revenue`() {
        val owner = registerUser("fee-pending-checkout-owner")
        val organization = createOrganization(owner)
        val household = createHousehold(organization, owner)

        val assignment =
            feeService.createAssignment(
                organization.id,
                household.id,
                null,
                null,
                "Fall Uniform Fee",
                7500L,
                "USD",
                LocalDate.now().minusDays(5),
                owner,
            )

        val afterConfirmedPayment =
            feeService.recordPayment(
                organization.id,
                assignment.assignment.id,
                4000L,
                PaymentMethod.CASH,
                LocalDate.now(),
                "Real, confirmed payment",
                owner,
            )
        assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, afterConfirmedPayment.assignment.status)
        assertEquals(3500L, afterConfirmedPayment.balance.balanceMinor)

        // A household starts an online checkout twice but never completes either one —
        // exactly the "abandoned checkout" scenario found live. These rows must never
        // affect any reported balance.
        feePaymentRepository.insertPendingOnline(
            organization.id,
            assignment.assignment.id,
            household.id,
            3500L,
            "USD",
            LocalDate.now(),
            owner.userId,
            "family@example.com",
            "Test Family",
        )
        feePaymentRepository.insertPendingOnline(
            organization.id,
            assignment.assignment.id,
            household.id,
            3500L,
            "USD",
            LocalDate.now(),
            owner.userId,
            "family@example.com",
            "Test Family",
        )

        val summary = feeRepository.getFinancialSummary(organization.id)
        assertEquals(4000L, summary.feesCollectedMinor, "pending checkouts must not count as collected revenue")
        assertEquals(3500L, summary.outstandingMinor, "the real outstanding balance must still reflect only the confirmed payment")

        val orgWideList = feeService.listForOrganization(organization.id, null, false, owner, 0, 20)
        val listedAssignment = orgWideList.single { it.id == assignment.assignment.id }
        assertEquals(
            4000L,
            listedAssignment.paidMinor,
            "the org-wide Collections list must show the same real paid total",
        )
        assertEquals(
            3500L,
            listedAssignment.balance.balanceMinor,
            "the org-wide Collections list must show the same real outstanding balance",
        )

        val overdueCount = actionCenterRepository.countOverdueFees(organization.id)
        assertEquals(
            1L,
            overdueCount,
            "a fee with a genuine unpaid balance past its due date must still count as overdue, pending checkouts notwithstanding",
        )
    }

    private fun registerUser(prefix: String): CurrentUser {
        val appUser = passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", "Test User")
        return passwordAuthenticationService.toCurrentUser(appUser)
    }

    private fun createOrganization(owner: CurrentUser): Organization =
        organizationService.create(
            "Riverside Soccer",
            "riverside-soccer-pending-checkout-${System.nanoTime()}",
            OrganizationType.RECREATIONAL_LEAGUE,
            owner,
        )

    private fun createHousehold(
        organization: Organization,
        owner: CurrentUser,
    ): Household = householdService.create(organization.id, "The Testerson Family", "family@example.com", null, null, owner)
}

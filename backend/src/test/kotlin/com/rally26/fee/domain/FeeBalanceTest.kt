package com.rally26.fee.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FeeBalanceTest {
    @Test
    fun `computeFeeBalance nets payments and adjustments`() {
        val balance = computeFeeBalance(originalAmountMinor = 15000L, paidMinor = 5000L, adjustedMinor = 2000L)
        assertEquals(FeeBalance(paidMinor = 5000L, adjustedMinor = 2000L, balanceMinor = 8000L), balance)
    }

    @Test
    fun `computeFeeBalance clamps an over-payment at zero, never negative`() {
        val balance = computeFeeBalance(originalAmountMinor = 15000L, paidMinor = 20000L, adjustedMinor = 0L)
        assertEquals(0L, balance.balanceMinor)
    }

    @Test
    fun `resolveStatusAfterBalanceChange moves to PAID when balance reaches zero`() {
        val balance = FeeBalance(paidMinor = 15000L, adjustedMinor = 0L, balanceMinor = 0L)
        val status = resolveStatusAfterBalanceChange(FeeAssignmentStatus.OPEN, balance, hasAnyActivePaymentOrAdjustment = true)
        assertEquals(FeeAssignmentStatus.PAID, status)
    }

    @Test
    fun `resolveStatusAfterBalanceChange moves to PARTIALLY_PAID with a positive balance and any activity`() {
        val balance = FeeBalance(paidMinor = 5000L, adjustedMinor = 0L, balanceMinor = 10000L)
        val status = resolveStatusAfterBalanceChange(FeeAssignmentStatus.OPEN, balance, hasAnyActivePaymentOrAdjustment = true)
        assertEquals(FeeAssignmentStatus.PARTIALLY_PAID, status)
    }

    @Test
    fun `resolveStatusAfterBalanceChange stays OPEN with no activity`() {
        val balance = FeeBalance(paidMinor = 0L, adjustedMinor = 0L, balanceMinor = 15000L)
        val status = resolveStatusAfterBalanceChange(FeeAssignmentStatus.OPEN, balance, hasAnyActivePaymentOrAdjustment = false)
        assertEquals(FeeAssignmentStatus.OPEN, status)
    }

    @Test
    fun `resolveStatusAfterBalanceChange never moves a WAIVED assignment`() {
        val balance = FeeBalance(paidMinor = 15000L, adjustedMinor = 0L, balanceMinor = 0L)
        val status = resolveStatusAfterBalanceChange(FeeAssignmentStatus.WAIVED, balance, hasAnyActivePaymentOrAdjustment = true)
        assertEquals(FeeAssignmentStatus.WAIVED, status)
    }

    @Test
    fun `resolveStatusAfterBalanceChange never moves a CANCELLED assignment`() {
        val balance = FeeBalance(paidMinor = 0L, adjustedMinor = 0L, balanceMinor = 15000L)
        val status = resolveStatusAfterBalanceChange(FeeAssignmentStatus.CANCELLED, balance, hasAnyActivePaymentOrAdjustment = false)
        assertEquals(FeeAssignmentStatus.CANCELLED, status)
    }
}

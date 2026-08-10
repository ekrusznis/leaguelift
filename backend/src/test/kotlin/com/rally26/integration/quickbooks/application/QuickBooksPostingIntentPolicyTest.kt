package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksFinancialSourceType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksPostingIntentInput
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickBooksPostingIntentPolicyTest {
    private val policy = QuickBooksPostingIntentPolicy()

    @Test
    fun `fee assessment explicitly requires receivable and program fee income`() {
        val definition = policy.definition(QuickBooksFinancialSourceType.PROGRAM_FEE_ASSESSMENT)

        assertEquals(
            setOf(QuickBooksMappingType.FEES_RECEIVABLE, QuickBooksMappingType.PROGRAM_FEE_INCOME),
            definition.legs.map { it.mappingType }.toSet(),
        )
    }

    @Test
    fun `posting validation rejects signed amount currency and date ambiguity`() {
        val result =
            policy.validate(
                QuickBooksPostingIntentInput(
                    sourceType = QuickBooksFinancialSourceType.MERCHANDISE_SALE,
                    sourceReference = "",
                    occurredOn = LocalDate.of(2026, 8, 1),
                    amountMinor = -2500,
                    currency = "usd",
                ),
                periodStart = LocalDate.of(2026, 8, 2),
                periodEnd = LocalDate.of(2026, 8, 31),
            )

        assertFalse(result.valid)
        assertEquals(4, result.diagnostics.size)
    }

    @Test
    fun `valid contribution intent returns deterministic required mappings`() {
        val result =
            policy.validate(
                QuickBooksPostingIntentInput(
                    sourceType = QuickBooksFinancialSourceType.CONTRIBUTION,
                    sourceReference = "contribution:123",
                    occurredOn = LocalDate.of(2026, 8, 9),
                    amountMinor = 5000,
                    currency = "USD",
                ),
                periodStart = LocalDate.of(2026, 8, 1),
                periodEnd = LocalDate.of(2026, 8, 31),
            )

        assertTrue(result.valid)
        assertEquals(
            setOf(QuickBooksMappingType.BANK_CLEARING, QuickBooksMappingType.CONTRIBUTION_INCOME),
            result.requiredMappings,
        )
    }
}

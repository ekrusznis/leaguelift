package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksAccountMapping
import com.rally26.integration.quickbooks.domain.QuickBooksMappingCompatibility
import com.rally26.integration.quickbooks.domain.QuickBooksMappingType
import com.rally26.integration.quickbooks.domain.QuickBooksMappingValidationStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickBooksAccountingMappingPolicyTest {
    private val policy = QuickBooksAccountingMappingPolicy()

    @Test
    fun `owner sees recommended warning and blocked choices without automatic configuration`() {
        val recommended = account("income", "Program Registration Revenue", "Income")
        val warning = account("asset", "Program Receivable Asset", "Other Current Asset")
        val blocked = account("payable", "Accounts Payable", "Accounts Payable")

        assertEquals(
            QuickBooksMappingCompatibility.RECOMMENDED,
            policy.evaluate(QuickBooksMappingType.PROGRAM_FEE_INCOME, recommended).compatibility,
        )
        assertEquals(
            QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING,
            policy.evaluate(QuickBooksMappingType.FEES_RECEIVABLE, warning).compatibility,
        )
        assertEquals(
            QuickBooksMappingCompatibility.BLOCKED,
            policy.evaluate(QuickBooksMappingType.PROGRAM_FEE_INCOME, blocked).compatibility,
        )
        assertFalse(policy.evaluate(QuickBooksMappingType.PROGRAM_FEE_INCOME, blocked).selectable)
    }

    @Test
    fun `inactive provider account is visible but never selectable`() {
        val inactive = account("old-income", "Legacy Program Income", "Income", active = false)

        val result = policy.evaluate(QuickBooksMappingType.PROGRAM_FEE_INCOME, inactive)

        assertEquals(QuickBooksMappingCompatibility.BLOCKED, result.compatibility)
        assertFalse(result.selectable)
    }

    @Test
    fun `recommendation uses owner chart names but does not guess when candidates tie`() {
        val merchandise = account("merch", "Merchandise Sales", "Income")
        val registration = account("fees", "Registration Revenue", "Income")

        val sales = policy.options(QuickBooksMappingType.SALES_INCOME, listOf(registration, merchandise))
        val fees = policy.options(QuickBooksMappingType.PROGRAM_FEE_INCOME, listOf(registration, merchandise))

        assertEquals("merch", sales.suggestedAccountId)
        assertEquals("fees", fees.suggestedAccountId)
        assertTrue(sales.accounts.single { it.account.id == "merch" }.suggested)
    }

    @Test
    fun `same provider account may intentionally serve multiple compatible owner mapping roles`() {
        val sharedIncome = account("shared-income", "Program Revenue", "Income")

        val fees = policy.evaluate(QuickBooksMappingType.PROGRAM_FEE_INCOME, sharedIncome)
        val merchandise = policy.evaluate(QuickBooksMappingType.SALES_INCOME, sharedIncome)
        val contributions = policy.evaluate(QuickBooksMappingType.CONTRIBUTION_INCOME, sharedIncome)

        assertEquals(QuickBooksMappingCompatibility.RECOMMENDED, fees.compatibility)
        assertEquals(QuickBooksMappingCompatibility.RECOMMENDED, merchandise.compatibility)
        assertEquals(QuickBooksMappingCompatibility.RECOMMENDED, contributions.compatibility)
        assertTrue(fees.selectable)
        assertTrue(merchandise.selectable)
        assertTrue(contributions.selectable)
    }

    @Test
    fun `ambiguous recommended accounts remain owner selected`() {
        val first = account("one", "Operating Income", "Income")
        val second = account("two", "General Income", "Income")

        val options = policy.options(QuickBooksMappingType.SPONSORSHIP_INCOME, listOf(first, second))

        assertNull(options.suggestedAccountId)
    }

    @Test
    fun `revalidation distinguishes inactive stale incompatible and acknowledged warning mappings`() {
        val now = Instant.parse("2026-08-09T12:00:00Z")
        val userId = UUID.randomUUID()
        val connectionId = UUID.randomUUID()
        val mappings =
            listOf(
                mapping(connectionId, userId, now, QuickBooksMappingType.PROGRAM_FEE_INCOME, "inactive", false),
                mapping(connectionId, userId, now, QuickBooksMappingType.SALES_INCOME, "missing", false),
                mapping(connectionId, userId, now, QuickBooksMappingType.REFUNDS, "expense", true),
                mapping(connectionId, userId, now, QuickBooksMappingType.FEES_RECEIVABLE, "bank", false),
            )
        val accounts =
            listOf(
                account("inactive", "Old Fee Income", "Income", active = false),
                account("expense", "Refund Expense", "Expense"),
                account("bank", "Checking", "Bank"),
            )

        val result = policy.validateMappings(mappings, accounts).associateBy { it.mappingType }

        assertEquals(QuickBooksMappingValidationStatus.INACTIVE, result.getValue(QuickBooksMappingType.PROGRAM_FEE_INCOME).status)
        assertEquals(QuickBooksMappingValidationStatus.ACCOUNT_NOT_FOUND, result.getValue(QuickBooksMappingType.SALES_INCOME).status)
        assertEquals(QuickBooksMappingValidationStatus.VALID_WITH_WARNING, result.getValue(QuickBooksMappingType.REFUNDS).status)
        assertEquals(QuickBooksMappingValidationStatus.INCOMPATIBLE, result.getValue(QuickBooksMappingType.FEES_RECEIVABLE).status)
        assertEquals(QuickBooksMappingValidationStatus.MISSING, result.getValue(QuickBooksMappingType.CONTRIBUTION_INCOME).status)
    }

    private fun account(
        id: String,
        name: String,
        type: String,
        active: Boolean = true,
    ) = QuickBooksAccount(
        id = id,
        name = name,
        fullyQualifiedName = name,
        accountType = type,
        accountSubType = null,
        classification = null,
        active = active,
    )

    private fun mapping(
        connectionId: UUID,
        userId: UUID,
        now: Instant,
        type: QuickBooksMappingType,
        accountId: String,
        warningAcknowledged: Boolean,
    ) = QuickBooksAccountMapping(
        id = UUID.randomUUID(),
        connectionId = connectionId,
        mappingType = type,
        externalAccountId = accountId,
        externalAccountName = accountId,
        externalAccountFullyQualifiedName = accountId,
        externalAccountType = null,
        externalAccountSubType = null,
        compatibilityAtSelection =
            if (warningAcknowledged) {
                QuickBooksMappingCompatibility.ALLOWED_WITH_WARNING
            } else {
                QuickBooksMappingCompatibility.RECOMMENDED
            },
        warningAcknowledged = warningAcknowledged,
        active = true,
        configuredByUserId = userId,
        createdAt = now,
        updatedAt = now,
    )
}

package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksCompany
import org.springframework.stereotype.Component

/**
 * Official QuickBooks HTTP calls remain intentionally absent during Phase 29. The seam is
 * deterministic in local/test; non-stub environments fail closed until a later explicitly
 * approved credentialed activation supplies an Intuit app, sandbox verification, scopes,
 * accounting review, and write policy.
 */
interface QuickBooksProviderClient {
    fun readCompany(
        accessToken: String,
        realmId: String,
    ): QuickBooksCompany

    fun listAccounts(
        accessToken: String,
        realmId: String,
    ): List<QuickBooksAccount>
}

@Component
class ScaffoldQuickBooksProviderClient(
    private val properties: IntegrationProperties,
) : QuickBooksProviderClient {
    override fun readCompany(
        accessToken: String,
        realmId: String,
    ): QuickBooksCompany {
        requireStub(accessToken)
        return QuickBooksCompany(realmId, "Rally26 Sandbox Organization", "US", "USD")
    }

    override fun listAccounts(
        accessToken: String,
        realmId: String,
    ): List<QuickBooksAccount> {
        requireStub(accessToken)
        return listOf(
            account("qb-income-fees", "Program Fee Income", "Income", "ServiceFeeIncome", "Revenue"),
            account("qb-income-sales", "Merchandise Sales", "Income", "SalesOfProductIncome", "Revenue"),
            account(
                "qb-income-contributions",
                "Contributions",
                "Other Income",
                "OtherMiscellaneousIncome",
                "Revenue",
            ),
            account("qb-income-sponsorships", "Sponsorship Income", "Income", "ServiceFeeIncome", "Revenue"),
            account("qb-refunds", "Refunds and Allowances", "Income", "DiscountsRefundsGiven", "Revenue"),
            account(
                "qb-fees-receivable",
                "Program Fees Receivable",
                "Accounts Receivable",
                "AccountsReceivable",
                "Asset",
            ),
            account("qb-bank-clearing", "Rally26 Clearing", "Bank", "Checking", "Asset"),
            account(
                "qb-payout-clearing",
                "Payout Clearing",
                "Other Current Asset",
                "OtherCurrentAssets",
                "Asset",
            ),
            account(
                "qb-inactive-income",
                "Legacy Program Income",
                "Income",
                "ServiceFeeIncome",
                "Revenue",
                active = false,
            ),
        )
    }

    private fun account(
        id: String,
        name: String,
        accountType: String,
        accountSubType: String,
        classification: String,
        active: Boolean = true,
    ) = QuickBooksAccount(
        id = id,
        name = name,
        fullyQualifiedName = name,
        accountType = accountType,
        accountSubType = accountSubType,
        classification = classification,
        active = active,
    )

    private fun requireStub(accessToken: String) {
        if (!properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "QUICKBOOKS_CLIENT_NOT_ACTIVATED",
                "QuickBooks Online is scaffolded but has not been activated with a verified Intuit client.",
            )
        }
    }
}

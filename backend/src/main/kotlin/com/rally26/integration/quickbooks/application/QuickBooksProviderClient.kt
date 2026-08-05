package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksCompany
import org.springframework.stereotype.Component

/**
 * Official QuickBooks HTTP calls are intentionally absent in Phase 19. The seam is
 * complete and deterministic in local/test; all other environments fail closed until
 * an Intuit app, sandbox company, scopes, response contracts, and accounting policy
 * are verified in Phase 20.
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
            QuickBooksAccount("qb-income-sales", "Merchandise Sales", "Income", true),
            QuickBooksAccount("qb-income-contributions", "Contributions", "Other Income", true),
            QuickBooksAccount("qb-income-sponsorships", "Sponsorship Income", "Income", true),
            QuickBooksAccount("qb-refunds", "Refunds and Allowances", "Income", true),
            QuickBooksAccount("qb-fees-receivable", "Program Fees Receivable", "Accounts Receivable", true),
            QuickBooksAccount("qb-bank-clearing", "Rally26 Clearing", "Bank", true),
            QuickBooksAccount("qb-payout-clearing", "Payout Clearing", "Other Current Asset", true),
        )
    }

    private fun requireStub(accessToken: String) {
        if (!properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "QUICKBOOKS_CLIENT_NOT_ACTIVATED",
                "QuickBooks Online is scaffolded but has not been activated with a verified Intuit client.",
            )
        }
    }
}

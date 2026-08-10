package com.rally26.integration.quickbooks.contract

import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.domain.QuickBooksCompany

/**
 * Fail-closed conversion from Intuit transport DTOs into the already-existing Phase 19
 * QuickBooks domain models. Missing identifiers, names, account types, or home currency are contract failures rather
 * than values Rally26 should guess. Country remains nullable in the existing domain.
 */
object QuickBooksContractMapper {
    fun toCompany(
        realmId: String,
        companyInfoResponse: QuickBooksCompanyInfoResponse,
        preferencesResponse: QuickBooksPreferencesResponse,
    ): QuickBooksCompany {
        val companyInfo =
            companyInfoResponse.companyInfo
                ?: throw QuickBooksContractException("CompanyInfo response did not contain CompanyInfo")
        val preferences =
            preferencesResponse.preferences
                ?: throw QuickBooksContractException("Preferences response did not contain Preferences")

        return QuickBooksCompany(
            realmId = realmId.requireContractValue("realmId"),
            companyName = companyInfo.companyName.requireContractValue("CompanyInfo.CompanyName"),
            country = companyInfo.country?.trim()?.takeIf { it.isNotEmpty() },
            defaultCurrency =
                preferences.currencyPrefs
                    ?.homeCurrency
                    ?.value
                    .requireContractValue("Preferences.CurrencyPrefs.HomeCurrency.value"),
        )
    }

    fun toAccounts(response: QuickBooksAccountQueryResponse): List<QuickBooksAccount> =
        response.queryResponse
            ?.accounts
            .orEmpty()
            .map { account ->
                QuickBooksAccount(
                    id = account.id.requireContractValue("Account.Id"),
                    name = account.name.requireContractValue("Account.Name"),
                    fullyQualifiedName = account.fullyQualifiedName?.trim()?.takeIf { it.isNotEmpty() },
                    accountType = account.accountType.requireContractValue("Account.AccountType"),
                    accountSubType = account.accountSubType?.trim()?.takeIf { it.isNotEmpty() },
                    classification = account.classification?.trim()?.takeIf { it.isNotEmpty() },
                    active = account.active ?: false,
                )
            }

    private fun String?.requireContractValue(field: String): String {
        val value = this?.trim()
        if (value.isNullOrEmpty()) {
            throw QuickBooksContractException("QuickBooks contract field $field is required")
        }
        return value
    }
}

class QuickBooksContractException(
    message: String,
) : IllegalStateException(message)

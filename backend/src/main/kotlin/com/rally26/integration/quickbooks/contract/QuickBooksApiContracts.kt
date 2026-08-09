package com.rally26.integration.quickbooks.contract

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Transport contracts for the QuickBooks Online Accounting API.
 *
 * Phase 29 keeps these DTOs deliberately separate from Rally26 domain models so a later
 * credentialed provider can evolve HTTP concerns without leaking Intuit wire shapes into
 * accounting or organization services.
 */
object QuickBooksApiContract {
    /**
     * Phase 29 fixture/contract baseline. Intuit currently treats 75 as the minimum/default
     * supported minor version for the QuickBooks Online Accounting API.
     */
    const val SCHEMA_MINOR_VERSION = 75
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksCompanyInfoResponse(
    @JsonProperty("CompanyInfo")
    val companyInfo: QuickBooksCompanyInfoPayload? = null,
    val time: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksCompanyInfoPayload(
    @JsonProperty("Id")
    val id: String? = null,
    @JsonProperty("CompanyName")
    val companyName: String? = null,
    @JsonProperty("Country")
    val country: String? = null,
    @JsonProperty("SyncToken")
    val syncToken: String? = null,
    @JsonProperty("MetaData")
    val metaData: QuickBooksMetaData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksPreferencesResponse(
    @JsonProperty("Preferences")
    val preferences: QuickBooksPreferencesPayload? = null,
    val time: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksPreferencesPayload(
    @JsonProperty("CurrencyPrefs")
    val currencyPrefs: QuickBooksCurrencyPreferences? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksCurrencyPreferences(
    @JsonProperty("MultiCurrencyEnabled")
    val multiCurrencyEnabled: Boolean? = null,
    @JsonProperty("HomeCurrency")
    val homeCurrency: QuickBooksReference? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksAccountQueryResponse(
    @JsonProperty("QueryResponse")
    val queryResponse: QuickBooksAccountQueryPayload? = null,
    val time: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksAccountQueryPayload(
    @JsonProperty("Account")
    val accounts: List<QuickBooksAccountPayload> = emptyList(),
    val startPosition: Int? = null,
    val maxResults: Int? = null,
    val totalCount: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksAccountPayload(
    @JsonProperty("Id")
    val id: String? = null,
    @JsonProperty("Name")
    val name: String? = null,
    @JsonProperty("FullyQualifiedName")
    val fullyQualifiedName: String? = null,
    @JsonProperty("Active")
    val active: Boolean? = null,
    @JsonProperty("Classification")
    val classification: String? = null,
    @JsonProperty("AccountType")
    val accountType: String? = null,
    @JsonProperty("AccountSubType")
    val accountSubType: String? = null,
    @JsonProperty("CurrencyRef")
    val currencyRef: QuickBooksReference? = null,
    @JsonProperty("SyncToken")
    val syncToken: String? = null,
    @JsonProperty("MetaData")
    val metaData: QuickBooksMetaData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksReference(
    val value: String? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksMetaData(
    @JsonProperty("CreateTime")
    val createTime: String? = null,
    @JsonProperty("LastUpdatedTime")
    val lastUpdatedTime: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksFaultResponse(
    @JsonProperty("Fault")
    val fault: QuickBooksFault? = null,
    val time: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksFault(
    @JsonProperty("Error")
    val errors: List<QuickBooksFaultError> = emptyList(),
    val type: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuickBooksFaultError(
    @JsonProperty("Message")
    val message: String? = null,
    @JsonProperty("Detail")
    val detail: String? = null,
    val code: String? = null,
    val element: String? = null,
)

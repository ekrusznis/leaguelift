package com.rally26.integration.quickbooks.contract

import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickBooksApiContractsTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `company and preferences fixtures map to existing company domain without guessed currency`() {
        val companyInfo = readFixture<QuickBooksCompanyInfoResponse>("company-info-v75.json")
        val preferences = readFixture<QuickBooksPreferencesResponse>("preferences-v75.json")

        val company =
            QuickBooksContractMapper.toCompany(
                realmId = "934145000000001",
                companyInfoResponse = companyInfo,
                preferencesResponse = preferences,
            )

        assertEquals("934145000000001", company.realmId)
        assertEquals("Rally26 Contract Sandbox", company.companyName)
        assertEquals("US", company.country)
        assertEquals("USD", company.defaultCurrency)
        assertEquals(75, QuickBooksApiContract.SCHEMA_MINOR_VERSION)
    }

    @Test
    fun `account query fixture preserves active state and ignores additive provider fields`() {
        val response = readFixture<QuickBooksAccountQueryResponse>("accounts-query-v75.json")

        val accounts = QuickBooksContractMapper.toAccounts(response)

        assertEquals(2, accounts.size)
        assertEquals("79", accounts[0].id)
        assertEquals("Merchandise Sales", accounts[0].name)
        assertEquals("Income", accounts[0].accountType)
        assertEquals("SalesOfProductIncome", accounts[0].accountSubType)
        assertEquals("Revenue", accounts[0].classification)
        assertEquals("Merchandise Sales", accounts[0].fullyQualifiedName)
        assertTrue(accounts[0].active)
        assertEquals("91", accounts[1].id)
        assertFalse(accounts[1].active)
    }

    @Test
    fun `validation fault fixture preserves Intuit fault classification and detail`() {
        val response = readFixture<QuickBooksFaultResponse>("validation-fault-v75.json")

        assertEquals("ValidationFault", response.fault?.type)
        assertEquals(1, response.fault?.errors?.size)
        assertEquals(
            "2020",
            response.fault
                ?.errors
                ?.single()
                ?.code,
        )
        assertEquals(
            "AccountRef",
            response.fault
                ?.errors
                ?.single()
                ?.element,
        )
        assertEquals(
            "Required parameter missing",
            response.fault
                ?.errors
                ?.single()
                ?.message,
        )
    }

    @Test
    fun `company conversion fails closed when provider omits home currency`() {
        val companyInfo = readFixture<QuickBooksCompanyInfoResponse>("company-info-v75.json")
        val preferences =
            QuickBooksPreferencesResponse(
                preferences =
                    QuickBooksPreferencesPayload(
                        currencyPrefs = QuickBooksCurrencyPreferences(homeCurrency = null),
                    ),
            )

        val error =
            assertFailsWith<QuickBooksContractException> {
                QuickBooksContractMapper.toCompany(
                    realmId = "934145000000001",
                    companyInfoResponse = companyInfo,
                    preferencesResponse = preferences,
                )
            }

        assertTrue(error.message.orEmpty().contains("HomeCurrency"))
    }

    private inline fun <reified T> readFixture(name: String): T {
        val resource =
            checkNotNull(javaClass.getResource("/fixtures/quickbooks/$name")) {
                "Missing QuickBooks contract fixture $name"
            }
        return resource.openStream().use { mapper.readValue<T>(it) }
    }
}

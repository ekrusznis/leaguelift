package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.web.QuickBooksAccountMappingResponse
import com.rally26.integration.quickbooks.web.QuickBooksAccountResponse
import com.rally26.integration.quickbooks.web.QuickBooksActivationReadinessResponse
import com.rally26.integration.quickbooks.web.QuickBooksConnectionSettingResponse
import com.rally26.integration.quickbooks.web.QuickBooksExportPreviewResponse
import com.rally26.integration.quickbooks.web.QuickBooksMappingOptionsResponse
import com.rally26.integration.quickbooks.web.QuickBooksMappingValidationResponse
import com.rally26.integration.quickbooks.web.QuickBooksOverviewResponse
import kotlin.test.Test
import kotlin.test.assertFalse

class QuickBooksResponsePrivacyTest {
    @Test
    fun `QuickBooks API response DTOs expose no credential or token fields`() {
        val responseTypes =
            listOf(
                QuickBooksOverviewResponse::class.java,
                QuickBooksConnectionSettingResponse::class.java,
                QuickBooksActivationReadinessResponse::class.java,
                QuickBooksAccountResponse::class.java,
                QuickBooksAccountMappingResponse::class.java,
                QuickBooksMappingOptionsResponse::class.java,
                QuickBooksMappingValidationResponse::class.java,
                QuickBooksExportPreviewResponse::class.java,
            )
        val forbiddenFragments =
            listOf(
                "accesstoken",
                "refreshtoken",
                "clientsecret",
                "credentialcipher",
                "ciphertext",
                "codeverifier",
                "pkce",
            )

        responseTypes.forEach { type ->
            type.declaredFields.forEach { field ->
                val normalized = field.name.lowercase()
                assertFalse(
                    forbiddenFragments.any(normalized::contains),
                    "${type.simpleName}.${field.name} must not expose provider credentials or OAuth secrets.",
                )
            }
        }
    }
}

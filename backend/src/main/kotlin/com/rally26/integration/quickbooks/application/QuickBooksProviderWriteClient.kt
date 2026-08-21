package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.quickbooks.domain.QuickBooksProviderReadbackResult
import com.rally26.integration.quickbooks.domain.QuickBooksProviderRequestPlan
import com.rally26.integration.quickbooks.domain.QuickBooksProviderWriteResult
import org.springframework.stereotype.Component

/**
 * Activation seam for future QuickBooks Accounting API writes/readback.
 *
 * Phase 29 intentionally provides only a fail-closed implementation. No stub or environment may
 * turn provider writes on; a later credentialed phase must replace this component only after
 * approved scopes, sandbox verification, accounting review, and an explicit write policy.
 */
interface QuickBooksProviderWriteClient {
    fun executeWrite(
        accessToken: String,
        realmId: String,
        request: QuickBooksProviderRequestPlan,
        canonicalPayload: String,
    ): QuickBooksProviderWriteResult

    fun readBack(
        accessToken: String,
        realmId: String,
        request: QuickBooksProviderRequestPlan,
    ): QuickBooksProviderReadbackResult
}

@Component
class DisabledQuickBooksProviderWriteClient : QuickBooksProviderWriteClient {
    override fun executeWrite(
        accessToken: String,
        realmId: String,
        request: QuickBooksProviderRequestPlan,
        canonicalPayload: String,
    ): QuickBooksProviderWriteResult = writesDisabled()

    override fun readBack(
        accessToken: String,
        realmId: String,
        request: QuickBooksProviderRequestPlan,
    ): QuickBooksProviderReadbackResult = writesDisabled()

    private fun writesDisabled(): Nothing =
        throw ServiceUnavailableException(
            "QUICKBOOKS_WRITES_DISABLED",
            "QuickBooks provider writes/readback are disabled until credentialed activation is approved.",
        )
}

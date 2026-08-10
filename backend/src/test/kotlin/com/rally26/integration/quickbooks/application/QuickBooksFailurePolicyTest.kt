package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.contract.QuickBooksFault
import com.rally26.integration.quickbooks.contract.QuickBooksFaultError
import com.rally26.integration.quickbooks.contract.QuickBooksFaultResponse
import com.rally26.integration.quickbooks.domain.QuickBooksFailureCategory
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksReadbackStrategy
import com.rally26.integration.quickbooks.domain.QuickBooksRetryDisposition
import com.rally26.integration.quickbooks.domain.QuickBooksTransportFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickBooksFailurePolicyTest {
    private val policy = QuickBooksFailurePolicy()

    @Test
    fun `429 waits at least sixty seconds and reuses the same request identity`() {
        val failure = policy.classify(429, null, "tid-rate-limit")
        val decision = policy.retryDecision(QuickBooksProviderOperationKind.CREATE, failure)

        assertEquals(QuickBooksFailureCategory.THROTTLED, failure.category)
        assertEquals(QuickBooksRetryDisposition.RETRY_SAME_REQUEST_AFTER_DELAY, decision.disposition)
        assertEquals(60L, decision.minimumDelaySeconds)
        assertTrue(decision.retryable)
    }

    @Test
    fun `transport timeout on create requires query readback before retry`() {
        val failure =
            policy.classify(
                httpStatus = null,
                faultResponse = null,
                transportFailure = QuickBooksTransportFailureKind.TIMEOUT,
            )
        val decision = policy.retryDecision(QuickBooksProviderOperationKind.CREATE, failure)

        assertEquals(QuickBooksFailureCategory.AMBIGUOUS_TRANSPORT, failure.category)
        assertEquals(QuickBooksRetryDisposition.READBACK_THEN_RETRY_SAME_REQUEST, decision.disposition)
        assertEquals(QuickBooksReadbackStrategy.QUERY_BY_STABLE_REFERENCE, decision.readbackStrategy)
        assertTrue(decision.retryable)
    }

    @Test
    fun `server failure on update requires entity readback before same request retry`() {
        val failure = policy.classify(503, systemFault("10000"), "tid-system")
        val decision = policy.retryDecision(QuickBooksProviderOperationKind.UPDATE, failure)

        assertEquals(QuickBooksFailureCategory.TRANSIENT_SYSTEM, failure.category)
        assertEquals(QuickBooksRetryDisposition.READBACK_THEN_RETRY_SAME_REQUEST, decision.disposition)
        assertEquals(QuickBooksReadbackStrategy.READ_BY_ENTITY_ID, decision.readbackStrategy)
    }

    @Test
    fun `stale object refreshes SyncToken instead of blind retry`() {
        val failure = policy.classify(400, validationFault("5010"))
        val decision = policy.retryDecision(QuickBooksProviderOperationKind.UPDATE, failure)

        assertEquals(QuickBooksFailureCategory.STALE_OBJECT, failure.category)
        assertEquals(QuickBooksRetryDisposition.REFRESH_ENTITY_THEN_REBUILD, decision.disposition)
        assertEquals(QuickBooksReadbackStrategy.READ_BY_ENTITY_ID, decision.readbackStrategy)
        assertTrue(decision.retryable)
    }

    @Test
    fun `validation and duplicate business keys are not automatically retried`() {
        val validation =
            policy.retryDecision(
                QuickBooksProviderOperationKind.CREATE,
                policy.classify(400, validationFault("6000")),
            )
        val duplicate =
            policy.retryDecision(
                QuickBooksProviderOperationKind.CREATE,
                policy.classify(400, validationFault("6140")),
            )

        assertEquals(QuickBooksRetryDisposition.DO_NOT_RETRY, validation.disposition)
        assertFalse(validation.retryable)
        assertEquals(QuickBooksRetryDisposition.MANUAL_REVIEW, duplicate.disposition)
        assertFalse(duplicate.retryable)
    }

    @Test
    fun `duplicate Intuit request id becomes readback event`() {
        val failure = policy.classify(400, validationFault("600"))
        val decision = policy.retryDecision(QuickBooksProviderOperationKind.CREATE, failure)

        assertEquals(QuickBooksFailureCategory.DUPLICATE_REQUEST_ID, failure.category)
        assertEquals(QuickBooksRetryDisposition.READBACK_REQUIRED, decision.disposition)
        assertEquals(QuickBooksReadbackStrategy.QUERY_BY_STABLE_REFERENCE, decision.readbackStrategy)
        assertFalse(decision.retryable)
    }

    private fun validationFault(code: String) =
        QuickBooksFaultResponse(
            fault =
                QuickBooksFault(
                    errors = listOf(QuickBooksFaultError(message = "Validation", detail = "Fixture $code", code = code)),
                    type = "ValidationFault",
                ),
        )

    private fun systemFault(code: String) =
        QuickBooksFaultResponse(
            fault =
                QuickBooksFault(
                    errors = listOf(QuickBooksFaultError(message = "System", detail = "Fixture $code", code = code)),
                    type = "SystemFault",
                ),
        )
}

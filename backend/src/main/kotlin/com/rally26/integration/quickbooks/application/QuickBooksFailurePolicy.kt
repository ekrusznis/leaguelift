package com.rally26.integration.quickbooks.application

import com.rally26.integration.quickbooks.contract.QuickBooksFaultResponse
import com.rally26.integration.quickbooks.domain.QuickBooksFailureCategory
import com.rally26.integration.quickbooks.domain.QuickBooksProviderFailure
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksReadbackStrategy
import com.rally26.integration.quickbooks.domain.QuickBooksRetryDecision
import com.rally26.integration.quickbooks.domain.QuickBooksRetryDisposition
import com.rally26.integration.quickbooks.domain.QuickBooksTransportFailureKind
import org.springframework.stereotype.Component

/**
 * Classifies Intuit HTTP/Fault responses separately from transport ambiguity and derives the
 * safe retry/readback action. Financial writes are never blindly repeated after an ambiguous
 * transport or server outcome.
 */
@Component
class QuickBooksFailurePolicy {
    fun classify(
        httpStatus: Int?,
        faultResponse: QuickBooksFaultResponse?,
        intuitTid: String? = null,
        transportFailure: QuickBooksTransportFailureKind? = null,
    ): QuickBooksProviderFailure {
        if (transportFailure != null) {
            return QuickBooksProviderFailure(
                category = QuickBooksFailureCategory.AMBIGUOUS_TRANSPORT,
                httpStatus = httpStatus,
                faultType = null,
                faultCode = null,
                message = "QuickBooks request outcome is ambiguous because the transport failed: ${transportFailure.name}.",
                intuitTid = intuitTid,
            )
        }

        val fault = faultResponse?.fault
        val firstError = fault?.errors?.firstOrNull()
        val code = firstError?.code?.trim()?.takeIf { it.isNotEmpty() }
        val faultType = fault?.type?.trim()?.takeIf { it.isNotEmpty() }
        val message =
            firstError?.detail?.trim()?.takeIf { it.isNotEmpty() }
                ?: firstError?.message?.trim()?.takeIf { it.isNotEmpty() }
                ?: "QuickBooks request failed${httpStatus?.let { " with HTTP $it" }.orEmpty()}."

        val category =
            when {
                httpStatus == 401 || faultType.equals("AuthenticationFault", ignoreCase = true) -> {
                    QuickBooksFailureCategory.AUTHENTICATION
                }

                httpStatus == 403 || faultType.equals("AuthorizationFault", ignoreCase = true) -> {
                    QuickBooksFailureCategory.AUTHORIZATION
                }

                httpStatus == 429 -> {
                    QuickBooksFailureCategory.THROTTLED
                }

                code == "5010" -> {
                    QuickBooksFailureCategory.STALE_OBJECT
                }

                code in MISSING_REFERENCE_CODES -> {
                    QuickBooksFailureCategory.MISSING_REFERENCE
                }

                code == "600" -> {
                    QuickBooksFailureCategory.DUPLICATE_REQUEST_ID
                }

                code in DUPLICATE_BUSINESS_KEY_CODES -> {
                    QuickBooksFailureCategory.DUPLICATE_BUSINESS_KEY
                }

                code in CLOSED_PERIOD_CODES -> {
                    QuickBooksFailureCategory.CLOSED_PERIOD
                }

                code in COMPANY_STATUS_CODES -> {
                    QuickBooksFailureCategory.COMPANY_STATUS
                }

                httpStatus != null && httpStatus >= 500 -> {
                    QuickBooksFailureCategory.TRANSIENT_SYSTEM
                }

                faultType.equals("SystemFault", ignoreCase = true) -> {
                    QuickBooksFailureCategory.TRANSIENT_SYSTEM
                }

                httpStatus != null && httpStatus in 400..499 -> {
                    QuickBooksFailureCategory.VALIDATION
                }

                faultType.equals("ValidationFault", ignoreCase = true) -> {
                    QuickBooksFailureCategory.VALIDATION
                }

                else -> {
                    QuickBooksFailureCategory.UNKNOWN
                }
            }

        return QuickBooksProviderFailure(category, httpStatus, faultType, code, message, intuitTid)
    }

    fun retryDecision(
        operationKind: QuickBooksProviderOperationKind,
        failure: QuickBooksProviderFailure,
    ): QuickBooksRetryDecision =
        when (failure.category) {
            QuickBooksFailureCategory.AUTHENTICATION -> {
                decision(
                    QuickBooksRetryDisposition.REFRESH_AUTH,
                    retryable = true,
                    reason = "Refresh the current OAuth token once; do not repeat the write with stale credentials.",
                )
            }

            QuickBooksFailureCategory.AUTHORIZATION -> {
                decision(
                    QuickBooksRetryDisposition.MANUAL_REVIEW,
                    reason = "The QuickBooks user or app no longer has the required authorization; reconnect or repair access.",
                )
            }

            QuickBooksFailureCategory.THROTTLED -> {
                decision(
                    QuickBooksRetryDisposition.RETRY_SAME_REQUEST_AFTER_DELAY,
                    retryable = true,
                    minimumDelaySeconds = INTUIT_THROTTLE_RETRY_SECONDS,
                    reason = "QuickBooks throttled the request; wait at least 60 seconds and reuse the exact same request ID and payload.",
                )
            }

            QuickBooksFailureCategory.STALE_OBJECT -> {
                decision(
                    QuickBooksRetryDisposition.REFRESH_ENTITY_THEN_REBUILD,
                    retryable = true,
                    readbackStrategy = QuickBooksReadbackStrategy.READ_BY_ENTITY_ID,
                    reason = "Read the latest entity/SyncToken and rebuild the update before any new write attempt.",
                )
            }

            QuickBooksFailureCategory.MISSING_REFERENCE -> {
                decision(
                    QuickBooksRetryDisposition.REFRESH_REFERENCE_DATA,
                    retryable = true,
                    reason = "Refresh referenced QuickBooks objects and mappings before rebuilding the request.",
                )
            }

            QuickBooksFailureCategory.DUPLICATE_REQUEST_ID -> {
                decision(
                    QuickBooksRetryDisposition.READBACK_REQUIRED,
                    retryable = false,
                    readbackStrategy = readbackStrategy(operationKind),
                    reason = "Treat a duplicate request ID as an idempotency/readback event, not as permission to issue a new write.",
                )
            }

            QuickBooksFailureCategory.TRANSIENT_SYSTEM,
            QuickBooksFailureCategory.AMBIGUOUS_TRANSPORT,
            -> {
                decision(
                    QuickBooksRetryDisposition.READBACK_THEN_RETRY_SAME_REQUEST,
                    retryable = true,
                    readbackStrategy = readbackStrategy(operationKind),
                    reason =
                        "The write outcome may be ambiguous. " +
                            "Read back first; retry only if absent, with the same request ID and identical payload.",
                )
            }

            QuickBooksFailureCategory.VALIDATION -> {
                decision(
                    QuickBooksRetryDisposition.DO_NOT_RETRY,
                    reason = "Fix the request or accounting data before creating a new provider request identity.",
                )
            }

            QuickBooksFailureCategory.DUPLICATE_BUSINESS_KEY,
            QuickBooksFailureCategory.CLOSED_PERIOD,
            QuickBooksFailureCategory.COMPANY_STATUS,
            QuickBooksFailureCategory.UNKNOWN,
            -> {
                decision(
                    QuickBooksRetryDisposition.MANUAL_REVIEW,
                    reason = "Do not automatically repeat this financial write; owner/accounting or provider-state review is required.",
                )
            }
        }

    private fun readbackStrategy(operationKind: QuickBooksProviderOperationKind): QuickBooksReadbackStrategy =
        when (operationKind) {
            QuickBooksProviderOperationKind.CREATE -> QuickBooksReadbackStrategy.QUERY_BY_STABLE_REFERENCE

            QuickBooksProviderOperationKind.UPDATE,
            QuickBooksProviderOperationKind.DELETE,
            -> QuickBooksReadbackStrategy.READ_BY_ENTITY_ID
        }

    private fun decision(
        disposition: QuickBooksRetryDisposition,
        retryable: Boolean = false,
        minimumDelaySeconds: Long? = null,
        readbackStrategy: QuickBooksReadbackStrategy = QuickBooksReadbackStrategy.NONE,
        reason: String,
    ) = QuickBooksRetryDecision(disposition, retryable, minimumDelaySeconds, readbackStrategy, reason)

    private companion object {
        const val INTUIT_THROTTLE_RETRY_SECONDS = 60L
        val MISSING_REFERENCE_CODES = setOf("610", "2500", "6250")
        val DUPLICATE_BUSINESS_KEY_CODES = setOf("630", "6140", "6240")
        val CLOSED_PERIOD_CODES = setOf("6200", "6210", "6540")
        val COMPANY_STATUS_CODES = setOf("140", "150", "6190", "10200")
    }
}

package com.rally26.integration.quickbooks.application

import com.rally26.common.error.ValidationException
import com.rally26.integration.quickbooks.domain.QuickBooksProviderOperationKind
import com.rally26.integration.quickbooks.domain.QuickBooksProviderRequestPlan
import com.rally26.integration.quickbooks.domain.QuickBooksReadbackStrategy
import com.rally26.integration.quickbooks.domain.QuickBooksRequestIdentity
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Builds deterministic identity for future QuickBooks write operations without sending them.
 *
 * Intuit request IDs are scoped to a QuickBooks company (realm) and should be reused for the
 * exact same write payload. Rally26 therefore keeps a stable business operation key separate
 * from the request ID: changing the canonical payload changes the request ID while preserving
 * the business-operation identity for conflict detection and review.
 */
@Component
class QuickBooksRequestIdentityPolicy {
    fun plan(
        organizationId: UUID,
        connectionId: UUID,
        realmId: String,
        sourceType: String,
        sourceReference: String,
        operationKind: QuickBooksProviderOperationKind,
        providerEntityType: String,
        canonicalPayload: String,
        readbackReference: String? = null,
    ): QuickBooksProviderRequestPlan {
        val normalizedRealmId = required(realmId, "QuickBooks realm ID")
        val normalizedSourceType = required(sourceType, "source type").uppercase(Locale.US)
        val normalizedSourceReference = required(sourceReference, "source reference")
        val normalizedEntityType = required(providerEntityType, "provider entity type").uppercase(Locale.US)
        val payload = required(canonicalPayload, "canonical provider payload")

        val operationKey =
            digest(
                listOf(
                    "qbo-op-v1",
                    organizationId.toString(),
                    connectionId.toString(),
                    normalizedRealmId,
                    normalizedSourceType,
                    normalizedSourceReference,
                    operationKind.name,
                    normalizedEntityType,
                ).joinToString("|"),
            )
        val payloadHash = digest(payload)
        val requestDigest = digest("qbo-request-v1|$operationKey|$payloadHash")
        val requestId = "r26-${requestDigest.take(INTUIT_REQUEST_ID_HASH_LENGTH)}"

        return QuickBooksProviderRequestPlan(
            identity = QuickBooksRequestIdentity(operationKey, payloadHash, requestId),
            operationKind = operationKind,
            providerEntityType = normalizedEntityType,
            sourceType = normalizedSourceType,
            sourceReference = normalizedSourceReference,
            readbackStrategy =
                when (operationKind) {
                    QuickBooksProviderOperationKind.CREATE -> QuickBooksReadbackStrategy.QUERY_BY_STABLE_REFERENCE
                    QuickBooksProviderOperationKind.UPDATE,
                    QuickBooksProviderOperationKind.DELETE,
                    -> QuickBooksReadbackStrategy.READ_BY_ENTITY_ID
                },
            readbackReference = readbackReference?.trim()?.takeIf { it.isNotEmpty() },
            providerWritesEnabled = false,
        )
    }

    private fun required(
        value: String,
        label: String,
    ): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw ValidationException("$label is required for a QuickBooks provider request plan.")

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val INTUIT_REQUEST_ID_MAX_LENGTH = 50
        const val INTUIT_REQUEST_ID_HASH_LENGTH = INTUIT_REQUEST_ID_MAX_LENGTH - 4
    }
}

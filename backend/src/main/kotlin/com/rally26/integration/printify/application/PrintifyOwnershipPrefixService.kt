package com.rally26.integration.printify.application

import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.store.persistence.StoreRepository
import org.springframework.stereotype.Service
import java.util.UUID

private const val MAX_PRINTIFY_TITLE_LENGTH = 250

/**
 * Phase 24 slice 24.4 (DESIGN-DOC.md section 14.1G, ADR-070): internal
 * traceability prefix for the one shared Rally26-controlled Printify
 * account. `store.slug` is already the existing org/team-scoping boundary
 * object (a store carries `organizationId` and an optional `teamId`), so
 * reusing `organizationSlug/storeSlug` avoids inventing a second, parallel
 * team-encoding scheme.
 *
 * This is traceability/defense-in-depth only — never a security boundary.
 * The real cross-organization isolation guarantee is the DB-level `unique`
 * constraints on `product_variant.printify_product_id` and
 * `fulfillment.printify_order_id` (migration V51), plus webhook resolution
 * keyed by those DB-unique values, never by trusting or parsing this
 * prefix out of an untrusted payload.
 */
@Service
class PrintifyOwnershipPrefixService(
    private val organizationRepository: OrganizationRepository,
    private val storeRepository: StoreRepository,
) {
    fun prefixFor(
        organizationId: UUID,
        storeId: UUID,
    ): String {
        val organization = organizationRepository.findById(organizationId) ?: error("organization $organizationId not found")
        val store = storeRepository.findById(storeId, organizationId) ?: error("store $storeId not found")
        return "${organization.slug}/${store.slug}"
    }

    /** Sent as Printify's product `title` — internal traceability only, never shown to a buyer or admin in our own UI. */
    fun productTitle(
        organizationId: UUID,
        storeId: UUID,
        productName: String,
    ): String = "[${prefixFor(organizationId, storeId)}] $productName".take(MAX_PRINTIFY_TITLE_LENGTH)

    /** Sent as Printify's order `external_id`. */
    fun orderExternalId(
        organizationId: UUID,
        storeId: UUID,
        orderId: UUID,
    ): String = "${prefixFor(organizationId, storeId)}:$orderId"

    /** Best-effort integrity check only — the resolved order id is never used as a lookup key, only compared against a value already resolved through a DB-unique column. */
    fun parseOrderId(externalId: String): UUID? = runCatching { UUID.fromString(externalId.substringAfterLast(':')) }.getOrNull()
}

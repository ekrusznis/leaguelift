package com.rally26.store.persistence

import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductVariant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val VARIANT_COLUMNS =
    "id, organization_id, product_id, catalog_source, label, sku, size, color, printify_print_provider_id, printify_variant_id, " +
        "currency, cost_minor, price_minor, is_active, print_area_width_px, print_area_height_px, " +
        "back_print_area_width_px, back_print_area_height_px, mockup_front_url, mockup_back_url, " +
        "printify_product_id, printify_shop_id, created_at, updated_at"

@Repository
class ProductVariantRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): ProductVariant? =
        jdbcClient
            .sql("select $VARIANT_COLUMNS from product_variant where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findActiveByProduct(productId: UUID): List<ProductVariant> =
        jdbcClient
            .sql("select $VARIANT_COLUMNS from product_variant where product_id = :productId and is_active = true order by created_at asc")
            .param("productId", productId)
            .query(::mapRow)
            .list()

    fun findByProduct(productId: UUID): List<ProductVariant> =
        jdbcClient
            .sql("select $VARIANT_COLUMNS from product_variant where product_id = :productId order by created_at asc")
            .param("productId", productId)
            .query(::mapRow)
            .list()

    fun insertPrintify(
        organizationId: UUID,
        productId: UUID,
        label: String,
        printifyPrintProviderId: Long,
        printifyVariantId: Long,
        currency: String,
        costMinor: Long,
        priceMinor: Long,
        printAreaWidthPx: Int? = null,
        printAreaHeightPx: Int? = null,
        backPrintAreaWidthPx: Int? = null,
        backPrintAreaHeightPx: Int? = null,
        mockupFrontUrl: String? = null,
        mockupBackUrl: String? = null,
        printifyProductId: String? = null,
        printifyShopId: String? = null,
    ): ProductVariant =
        insert(
            organizationId,
            productId,
            CatalogSource.PRINTIFY,
            label,
            null,
            null,
            null,
            printifyPrintProviderId,
            printifyVariantId,
            currency,
            costMinor,
            priceMinor,
            printAreaWidthPx,
            printAreaHeightPx,
            backPrintAreaWidthPx,
            backPrintAreaHeightPx,
            mockupFrontUrl,
            mockupBackUrl,
            printifyProductId,
            printifyShopId,
        )

    fun insertManual(
        organizationId: UUID,
        productId: UUID,
        label: String,
        sku: String?,
        size: String?,
        color: String?,
        currency: String,
        costMinor: Long,
        priceMinor: Long,
    ): ProductVariant =
        insert(
            organizationId,
            productId,
            CatalogSource.MANUAL,
            label,
            sku,
            size,
            color,
            null,
            null,
            currency,
            costMinor,
            priceMinor,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    private fun insert(
        organizationId: UUID,
        productId: UUID,
        catalogSource: CatalogSource,
        label: String,
        sku: String?,
        size: String?,
        color: String?,
        printifyPrintProviderId: Long?,
        printifyVariantId: Long?,
        currency: String,
        costMinor: Long,
        priceMinor: Long,
        printAreaWidthPx: Int? = null,
        printAreaHeightPx: Int? = null,
        backPrintAreaWidthPx: Int? = null,
        backPrintAreaHeightPx: Int? = null,
        mockupFrontUrl: String? = null,
        mockupBackUrl: String? = null,
        printifyProductId: String? = null,
        printifyShopId: String? = null,
    ): ProductVariant {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into product_variant
                	(id, organization_id, product_id, catalog_source, label, sku, size, color,
                	 printify_print_provider_id, printify_variant_id, currency, cost_minor, price_minor,
                	 print_area_width_px, print_area_height_px, back_print_area_width_px, back_print_area_height_px,
                	 mockup_front_url, mockup_back_url, printify_product_id, printify_shop_id, is_active, created_at, updated_at)
                values
                	(:id, :organizationId, :productId, :catalogSource, :label, :sku, :size, :color,
                	 :printifyPrintProviderId, :printifyVariantId, :currency, :costMinor, :priceMinor,
                	 :printAreaWidthPx, :printAreaHeightPx, :backPrintAreaWidthPx, :backPrintAreaHeightPx,
                	 :mockupFrontUrl, :mockupBackUrl, :printifyProductId, :printifyShopId, true, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("productId", productId)
            .param("catalogSource", catalogSource.name)
            .param("label", label)
            .param("sku", sku)
            .param("size", size)
            .param("color", color)
            .param("printifyPrintProviderId", printifyPrintProviderId)
            .param("printifyVariantId", printifyVariantId)
            .param("currency", currency)
            .param("costMinor", costMinor)
            .param("priceMinor", priceMinor)
            .param("printAreaWidthPx", printAreaWidthPx)
            .param("printAreaHeightPx", printAreaHeightPx)
            .param("backPrintAreaWidthPx", backPrintAreaWidthPx)
            .param("backPrintAreaHeightPx", backPrintAreaHeightPx)
            .param("mockupFrontUrl", mockupFrontUrl)
            .param("mockupBackUrl", mockupBackUrl)
            .param("printifyProductId", printifyProductId)
            .param("printifyShopId", printifyShopId)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun updateManual(
        id: UUID,
        organizationId: UUID,
        label: String,
        sku: String?,
        size: String?,
        color: String?,
        currency: String,
        costMinor: Long,
        priceMinor: Long,
        isActive: Boolean,
    ): Int =
        jdbcClient
            .sql(
                """
                update product_variant
                set label = :label, sku = :sku, size = :size, color = :color, currency = :currency,
                    cost_minor = :costMinor, price_minor = :priceMinor, is_active = :isActive, updated_at = :now
                where id = :id and organization_id = :organizationId and catalog_source = 'MANUAL'
                """.trimIndent(),
            ).param("label", label)
            .param("sku", sku)
            .param("size", size)
            .param("color", color)
            .param("currency", currency)
            .param("costMinor", costMinor)
            .param("priceMinor", priceMinor)
            .param("isActive", isActive)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun updateActive(
        id: UUID,
        organizationId: UUID,
        isActive: Boolean,
    ): Int =
        jdbcClient
            .sql(
                "update product_variant set is_active = :isActive, updated_at = :now where id = :id and organization_id = :organizationId",
            ).param("isActive", isActive)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun deleteByProduct(productId: UUID): Int =
        jdbcClient
            .sql("delete from product_variant where product_id = :productId")
            .param("productId", productId)
            .update()

    private fun mapRow(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): ProductVariant =
        ProductVariant(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            productId = rs.getObject("product_id", UUID::class.java),
            catalogSource = CatalogSource.valueOf(rs.getString("catalog_source")),
            label = rs.getString("label"),
            sku = rs.getString("sku"),
            size = rs.getString("size"),
            color = rs.getString("color"),
            printifyPrintProviderId = rs.getObject("printify_print_provider_id", java.lang.Long::class.java)?.toLong(),
            printifyVariantId = rs.getObject("printify_variant_id", java.lang.Long::class.java)?.toLong(),
            currency = rs.getString("currency"),
            costMinor = rs.getLong("cost_minor"),
            priceMinor = rs.getLong("price_minor"),
            isActive = rs.getBoolean("is_active"),
            printAreaWidthPx = rs.getObject("print_area_width_px", java.lang.Integer::class.java)?.toInt(),
            printAreaHeightPx = rs.getObject("print_area_height_px", java.lang.Integer::class.java)?.toInt(),
            backPrintAreaWidthPx = rs.getObject("back_print_area_width_px", java.lang.Integer::class.java)?.toInt(),
            backPrintAreaHeightPx = rs.getObject("back_print_area_height_px", java.lang.Integer::class.java)?.toInt(),
            mockupFrontUrl = rs.getString("mockup_front_url"),
            mockupBackUrl = rs.getString("mockup_back_url"),
            printifyProductId = rs.getString("printify_product_id"),
            printifyShopId = rs.getString("printify_shop_id"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}

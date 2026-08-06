package com.rally26.store.persistence

import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val PRODUCT_COLUMNS = """
    p.id, p.organization_id, p.store_id, p.name, p.description, p.catalog_source,
    p.manual_vendor_id, mv.name as manual_vendor_name, p.printify_blueprint_id,
    p.printify_image_id, p.printify_print_position, p.swag_logo_media_asset_id,
    p.swag_brand_asset_id, p.status, p.created_at, p.updated_at
"""

@Repository
class ProductRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Product? =
        jdbcClient
            .sql(
                """
                select $PRODUCT_COLUMNS from product p
                left join manual_vendor mv on mv.id = p.manual_vendor_id
                where p.id = :id and p.organization_id = :organizationId
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByStore(
        storeId: UUID,
        offset: Int,
        limit: Int,
        includeArchived: Boolean = false,
    ): List<Product> =
        jdbcClient
            .sql(
                """
                select $PRODUCT_COLUMNS from product p
                left join manual_vendor mv on mv.id = p.manual_vendor_id
                where p.store_id = :storeId
                  and (:includeArchived = true or p.status <> 'ARCHIVED')
                order by p.created_at desc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("storeId", storeId)
            .param("includeArchived", includeArchived)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countByStore(
        storeId: UUID,
        includeArchived: Boolean = false,
    ): Long =
        jdbcClient
            .sql("select count(*) from product where store_id = :storeId and (:includeArchived = true or status <> 'ARCHIVED')")
            .param("storeId", storeId)
            .param("includeArchived", includeArchived)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        storeId: UUID,
        name: String,
        description: String?,
        catalogSource: CatalogSource,
        manualVendorId: UUID?,
        printifyBlueprintId: Long?,
        printifyPrintPosition: String,
    ): Product {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient
            .sql(
                """
                insert into product
                	(id, organization_id, store_id, name, description, catalog_source, manual_vendor_id,
                	 printify_blueprint_id, printify_print_position, status, created_at, updated_at)
                values
                	(:id, :organizationId, :storeId, :name, :description, :catalogSource, :manualVendorId,
                	 :printifyBlueprintId, :printifyPrintPosition, 'DRAFT', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("storeId", storeId)
            .param("name", name)
            .param("description", description)
            .param("catalogSource", catalogSource.name)
            .param("manualVendorId", manualVendorId)
            .param("printifyBlueprintId", printifyBlueprintId)
            .param("printifyPrintPosition", printifyPrintPosition)
            .param("now", Timestamp.from(now))
            .update()
        return findById(id, organizationId)!!
    }

    fun updateManualProduct(
        id: UUID,
        organizationId: UUID,
        name: String,
        description: String?,
        manualVendorId: UUID?,
    ): Int =
        jdbcClient
            .sql(
                """
                update product
                set name = :name, description = :description, manual_vendor_id = :manualVendorId, updated_at = :now
                where id = :id and organization_id = :organizationId and catalog_source = 'MANUAL'
                """.trimIndent(),
            ).param("name", name)
            .param("description", description)
            .param("manualVendorId", manualVendorId)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun updatePrintifyImageId(
        id: UUID,
        organizationId: UUID,
        printifyImageId: String,
    ): Int =
        jdbcClient
            .sql(
                "update product set printify_image_id = :printifyImageId, updated_at = :now where id = :id and organization_id = :organizationId and catalog_source = 'PRINTIFY'",
            ).param("printifyImageId", printifyImageId)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    fun updateStatus(
        id: UUID,
        organizationId: UUID,
        status: ProductStatus,
    ): Int =
        jdbcClient
            .sql("update product set status = :status, updated_at = :now where id = :id and organization_id = :organizationId")
            .param("status", status.name)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    /** Swag Shop (DESIGN-DOC.md section 13): freezes the team's current logo onto the product for order-time compositing. */
    fun updateSwagLogoMediaAssetId(
        id: UUID,
        organizationId: UUID,
        swagLogoMediaAssetId: UUID,
    ): Int =
        jdbcClient
            .sql(
                "update product set swag_logo_media_asset_id = :swagLogoMediaAssetId, updated_at = :now where id = :id and organization_id = :organizationId",
            ).param("swagLogoMediaAssetId", swagLogoMediaAssetId)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    /** Snapshots both reusable-asset provenance and the exact media used for fulfillment. */
    fun updateSwagBrandAssetSnapshot(
        id: UUID,
        organizationId: UUID,
        swagBrandAssetId: UUID,
        swagLogoMediaAssetId: UUID,
    ): Int =
        jdbcClient
            .sql(
                """
                update product
                set swag_brand_asset_id = :swagBrandAssetId,
                    swag_logo_media_asset_id = :swagLogoMediaAssetId,
                    updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("swagBrandAssetId", swagBrandAssetId)
            .param("swagLogoMediaAssetId", swagLogoMediaAssetId)
            .param("now", Timestamp.from(Instant.now()))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    /** True if any variant of this product has ever appeared on a paid order — blocks deletion. */
    fun hasOrderHistory(productId: UUID): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                    select 1 from order_item oi
                    join product_variant pv on pv.id = oi.product_variant_id
                    where pv.product_id = :productId
                )
                """.trimIndent(),
            ).param("productId", productId)
            .query(Boolean::class.java)
            .single()

    fun delete(
        id: UUID,
        organizationId: UUID,
    ): Int =
        jdbcClient
            .sql("delete from product where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: java.sql.ResultSet,
        _rowNum: Int,
    ): Product =
        Product(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            storeId = rs.getObject("store_id", UUID::class.java),
            name = rs.getString("name"),
            description = rs.getString("description"),
            catalogSource = CatalogSource.valueOf(rs.getString("catalog_source")),
            manualVendorId = rs.getObject("manual_vendor_id", UUID::class.java),
            manualVendorName = rs.getString("manual_vendor_name"),
            printifyBlueprintId = rs.getObject("printify_blueprint_id", java.lang.Long::class.java)?.toLong(),
            printifyImageId = rs.getString("printify_image_id"),
            printifyPrintPosition = rs.getString("printify_print_position"),
            swagLogoMediaAssetId = rs.getObject("swag_logo_media_asset_id", UUID::class.java),
            swagBrandAssetId = rs.getObject("swag_brand_asset_id", UUID::class.java),
            status = ProductStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}

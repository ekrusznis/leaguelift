package com.leaguelift.store.persistence

import com.leaguelift.store.domain.ProductVariant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, product_id, label, printify_print_provider_id, printify_variant_id, currency, cost_minor, price_minor, is_active, created_at, updated_at"

@Repository
class ProductVariantRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): ProductVariant? =
		jdbcClient.sql("select $COLUMNS from product_variant where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findActiveByProduct(productId: UUID): List<ProductVariant> =
		jdbcClient.sql("select $COLUMNS from product_variant where product_id = :productId and is_active = true order by created_at asc")
			.param("productId", productId)
			.query(::mapRow)
			.list()

	fun insert(
		organizationId: UUID,
		productId: UUID,
		label: String,
		printifyPrintProviderId: Long,
		printifyVariantId: Long,
		currency: String,
		costMinor: Long,
		priceMinor: Long,
	): ProductVariant {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into product_variant
				(id, organization_id, product_id, label, printify_print_provider_id, printify_variant_id, currency, cost_minor, price_minor, is_active, created_at, updated_at)
			values
				(:id, :organizationId, :productId, :label, :printifyPrintProviderId, :printifyVariantId, :currency, :costMinor, :priceMinor, true, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("productId", productId)
			.param("label", label)
			.param("printifyPrintProviderId", printifyPrintProviderId)
			.param("printifyVariantId", printifyVariantId)
			.param("currency", currency)
			.param("costMinor", costMinor)
			.param("priceMinor", priceMinor)
			.param("now", Timestamp.from(now))
			.update()
		return ProductVariant(id, organizationId, productId, label, printifyPrintProviderId, printifyVariantId, currency, costMinor, priceMinor, true, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): ProductVariant =
		ProductVariant(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			productId = rs.getObject("product_id", UUID::class.java),
			label = rs.getString("label"),
			printifyPrintProviderId = rs.getLong("printify_print_provider_id"),
			printifyVariantId = rs.getLong("printify_variant_id"),
			currency = rs.getString("currency"),
			costMinor = rs.getLong("cost_minor"),
			priceMinor = rs.getLong("price_minor"),
			isActive = rs.getBoolean("is_active"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}

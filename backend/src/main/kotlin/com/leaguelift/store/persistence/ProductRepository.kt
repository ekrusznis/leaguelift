package com.leaguelift.store.persistence

import com.leaguelift.store.domain.Product
import com.leaguelift.store.domain.ProductStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS = "id, organization_id, store_id, name, description, printify_blueprint_id, printify_image_id, printify_print_position, status, created_at, updated_at"

@Repository
class ProductRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): Product? =
		jdbcClient.sql("select $COLUMNS from product where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByStore(storeId: UUID, offset: Int, limit: Int): List<Product> =
		jdbcClient.sql(
			"""
			select $COLUMNS from product
			where store_id = :storeId
			order by created_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("storeId", storeId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countByStore(storeId: UUID): Long =
		jdbcClient.sql("select count(*) from product where store_id = :storeId")
			.param("storeId", storeId)
			.query(Long::class.java)
			.single()

	fun insert(
		organizationId: UUID,
		storeId: UUID,
		name: String,
		description: String?,
		printifyBlueprintId: Long,
		printifyPrintPosition: String,
	): Product {
		val id = UUID.randomUUID()
		val now = Instant.now()
		jdbcClient.sql(
			"""
			insert into product
				(id, organization_id, store_id, name, description, printify_blueprint_id, printify_print_position, status, created_at, updated_at)
			values
				(:id, :organizationId, :storeId, :name, :description, :printifyBlueprintId, :printifyPrintPosition, 'DRAFT', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("storeId", storeId)
			.param("name", name)
			.param("description", description)
			.param("printifyBlueprintId", printifyBlueprintId)
			.param("printifyPrintPosition", printifyPrintPosition)
			.param("now", Timestamp.from(now))
			.update()
		return Product(id, organizationId, storeId, name, description, printifyBlueprintId, null, printifyPrintPosition, ProductStatus.DRAFT, now, now)
	}

	fun updatePrintifyImageId(id: UUID, organizationId: UUID, printifyImageId: String): Int {
		val now = Instant.now()
		return jdbcClient.sql("update product set printify_image_id = :printifyImageId, updated_at = :now where id = :id and organization_id = :organizationId")
			.param("printifyImageId", printifyImageId)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	fun updateStatus(id: UUID, organizationId: UUID, status: ProductStatus): Int {
		val now = Instant.now()
		return jdbcClient.sql("update product set status = :status, updated_at = :now where id = :id and organization_id = :organizationId")
			.param("status", status.name)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Product =
		Product(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			storeId = rs.getObject("store_id", UUID::class.java),
			name = rs.getString("name"),
			description = rs.getString("description"),
			printifyBlueprintId = rs.getLong("printify_blueprint_id"),
			printifyImageId = rs.getString("printify_image_id"),
			printifyPrintPosition = rs.getString("printify_print_position"),
			status = ProductStatus.valueOf(rs.getString("status")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}

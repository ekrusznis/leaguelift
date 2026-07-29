package com.leaguelift.order.persistence

import com.leaguelift.order.domain.OrderItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val COLUMNS = "id, order_id, product_variant_id, quantity, unit_price_minor, unit_cost_minor"

@Repository
class OrderItemRepository(private val jdbcClient: JdbcClient) {

	fun findByOrder(orderId: UUID): List<OrderItem> =
		jdbcClient.sql("select $COLUMNS from order_item where order_id = :orderId")
			.param("orderId", orderId)
			.query(::mapRow)
			.list()

	fun insert(orderId: UUID, productVariantId: UUID, quantity: Int, unitPriceMinor: Long, unitCostMinor: Long): OrderItem {
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into order_item (id, order_id, product_variant_id, quantity, unit_price_minor, unit_cost_minor)
			values (:id, :orderId, :productVariantId, :quantity, :unitPriceMinor, :unitCostMinor)
			""".trimIndent(),
		)
			.param("id", id)
			.param("orderId", orderId)
			.param("productVariantId", productVariantId)
			.param("quantity", quantity)
			.param("unitPriceMinor", unitPriceMinor)
			.param("unitCostMinor", unitCostMinor)
			.update()
		return OrderItem(id, orderId, productVariantId, quantity, unitPriceMinor, unitCostMinor)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): OrderItem =
		OrderItem(
			id = rs.getObject("id", UUID::class.java),
			orderId = rs.getObject("order_id", UUID::class.java),
			productVariantId = rs.getObject("product_variant_id", UUID::class.java),
			quantity = rs.getInt("quantity"),
			unitPriceMinor = rs.getLong("unit_price_minor"),
			unitCostMinor = rs.getLong("unit_cost_minor"),
		)
}

package com.rally26.order.persistence

import com.rally26.order.domain.OrderItem
import com.rally26.order.domain.PersonalizationPlacement
import com.rally26.order.domain.SwagLogoSize
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val COLUMNS =
    "id, order_id, product_variant_id, quantity, unit_price_minor, unit_cost_minor, " +
        "participant_id, personalization_name, personalization_number, personalization_placement, " +
        "personalization_logo_size"

@Repository
class OrderItemRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByOrder(orderId: UUID): List<OrderItem> =
        jdbcClient
            .sql("select $COLUMNS from order_item where order_id = :orderId")
            .param("orderId", orderId)
            .query(::mapRow)
            .list()

    fun insert(
        orderId: UUID,
        productVariantId: UUID,
        quantity: Int,
        unitPriceMinor: Long,
        unitCostMinor: Long,
        participantId: UUID? = null,
        personalizationName: String? = null,
        personalizationNumber: String? = null,
        personalizationPlacement: PersonalizationPlacement? = null,
        personalizationLogoSize: SwagLogoSize? = null,
    ): OrderItem {
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into order_item
                	(id, order_id, product_variant_id, quantity, unit_price_minor, unit_cost_minor,
                	 participant_id, personalization_name, personalization_number, personalization_placement,
                	 personalization_logo_size)
                values
                	(:id, :orderId, :productVariantId, :quantity, :unitPriceMinor, :unitCostMinor,
                	 :participantId, :personalizationName, :personalizationNumber, :personalizationPlacement,
                	 :personalizationLogoSize)
                """.trimIndent(),
            ).param("id", id)
            .param("orderId", orderId)
            .param("productVariantId", productVariantId)
            .param("quantity", quantity)
            .param("unitPriceMinor", unitPriceMinor)
            .param("unitCostMinor", unitCostMinor)
            .param("participantId", participantId)
            .param("personalizationName", personalizationName)
            .param("personalizationNumber", personalizationNumber)
            .param("personalizationPlacement", personalizationPlacement?.name)
            .param("personalizationLogoSize", personalizationLogoSize?.name)
            .update()
        return OrderItem(
            id,
            orderId,
            productVariantId,
            quantity,
            unitPriceMinor,
            unitCostMinor,
            participantId,
            personalizationName,
            personalizationNumber,
            personalizationPlacement,
            personalizationLogoSize,
        )
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrderItem =
        OrderItem(
            id = rs.getObject("id", UUID::class.java),
            orderId = rs.getObject("order_id", UUID::class.java),
            productVariantId = rs.getObject("product_variant_id", UUID::class.java),
            quantity = rs.getInt("quantity"),
            unitPriceMinor = rs.getLong("unit_price_minor"),
            unitCostMinor = rs.getLong("unit_cost_minor"),
            participantId = rs.getObject("participant_id", UUID::class.java),
            personalizationName = rs.getString("personalization_name"),
            personalizationNumber = rs.getString("personalization_number"),
            personalizationPlacement = rs.getString("personalization_placement")?.let { PersonalizationPlacement.valueOf(it) },
            personalizationLogoSize = rs.getString("personalization_logo_size")?.let { SwagLogoSize.valueOf(it) },
        )
}

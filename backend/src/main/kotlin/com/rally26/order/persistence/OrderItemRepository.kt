package com.rally26.order.persistence

import com.rally26.order.domain.OrderItem
import com.rally26.order.domain.OrderStatus
import com.rally26.order.domain.PersonalizationPlacement
import com.rally26.order.domain.SwagLogoSize
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, order_id, product_variant_id, quantity, unit_price_minor, unit_cost_minor, " +
        "participant_id, personalization_name, personalization_number, personalization_placement, " +
        "personalization_logo_size"

/** Swag Shop "my past orders" (see OrderService.listMySwagShopOrders): an order_item paired with the confirmation timestamp/store of its parent order, so a buyer-facing history list doesn't need a second round trip per item. */
data class OrderItemWithOrder(
    val orderId: UUID,
    val storeId: UUID,
    val confirmedAt: Instant,
    val currency: String,
    val item: OrderItem,
)

/** Cap for OrderItemRepository.findConfirmedByParticipants — a buyer-facing "my past orders" list, not paginated (mirrors how FeeAssignmentsPanel lists a household's fees in full). */
private const val MY_ORDERS_LIMIT = 100

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

    /** Swag Shop "my past orders" (see OrderService.listMySwagShopOrders) — confirmed order items for a caller-resolved set of participant ids, newest first. Returns an empty list without querying when [participantIds] is empty (an empty SQL `in (...)` is invalid). */
    fun findConfirmedByParticipants(
        organizationId: UUID,
        participantIds: List<UUID>,
    ): List<OrderItemWithOrder> {
        if (participantIds.isEmpty()) return emptyList()
        return jdbcClient
            .sql(
                """
                select o.id as parent_order_id, o.store_id as parent_store_id, o.confirmed_at as parent_confirmed_at,
                       o.currency as parent_currency,
                       oi.id, oi.order_id, oi.product_variant_id, oi.quantity, oi.unit_price_minor, oi.unit_cost_minor,
                       oi.participant_id, oi.personalization_name, oi.personalization_number,
                       oi.personalization_placement, oi.personalization_logo_size
                from order_item oi
                join "order" o on o.id = oi.order_id
                where o.organization_id = :organizationId
                  and o.status = :status
                  and oi.participant_id in (:participantIds)
                order by o.confirmed_at desc
                limit $MY_ORDERS_LIMIT
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("status", OrderStatus.CONFIRMED.name)
            .param("participantIds", participantIds)
            .query(::mapRowWithOrder)
            .list()
    }

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

    private fun mapRowWithOrder(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): OrderItemWithOrder =
        OrderItemWithOrder(
            orderId = rs.getObject("parent_order_id", UUID::class.java),
            storeId = rs.getObject("parent_store_id", UUID::class.java),
            confirmedAt = rs.getTimestamp("parent_confirmed_at").toInstant(),
            currency = rs.getString("parent_currency"),
            item = mapRow(rs, rowNum),
        )
}

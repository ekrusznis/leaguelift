package com.rally26.order.domain

import com.rally26.finance.domain.PaymentSource

data class OrderSearchCriteria(
    val keyword: String? = null,
    val status: OrderStatus? = null,
    val paymentSource: PaymentSource? = null,
    val fulfillmentStatus: FulfillmentStatus? = null,
    val sort: OrderSearchSort = OrderSearchSort.NEWEST,
)

enum class OrderSearchSort { NEWEST, OLDEST, SUPPORTER_ASC, STATUS_ASC, FULFILLMENT_ASC }

/** [OrderRepository.search]'s result row — a plain [Order] doesn't carry fulfillment status, which lives in a separate 1:1 table. */
data class OrderSearchRow(
    val order: Order,
    val fulfillmentStatus: FulfillmentStatus?,
)

package com.rally26.order.domain

import java.util.UUID

enum class PersonalizationPlacement { LEFT_CHEST, RIGHT_CHEST, BACK }

data class OrderItem(
    val id: UUID,
    val orderId: UUID,
    val productVariantId: UUID,
    val quantity: Int,
    /** Both snapshotted at order time — "must store transaction-time cost" (DESIGN-DOC.md section 14.3 Apparel acceptance criteria). */
    val unitPriceMinor: Long,
    val unitCostMinor: Long,
    /** Swag Shop (DESIGN-DOC.md section 13, Path 1/Quick): which athlete this item is for, and their optional name/number personalization. All null for a non-personalized Swag Shop/store item — see OrderService.createInitialFulfillment's static-vs-composited branch. */
    val participantId: UUID? = null,
    val personalizationName: String? = null,
    val personalizationNumber: String? = null,
    val personalizationPlacement: PersonalizationPlacement? = null,
)

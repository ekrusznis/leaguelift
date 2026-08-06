package com.rally26.order.domain

import java.util.UUID

enum class PersonalizationPlacement { LEFT_CHEST, RIGHT_CHEST, CENTER_FRONT, BACK }

/** Swag Shop Path 2 (DESIGN-DOC.md section 14.1G, 24.2): a curated logo-size preset, never a freeform scale value. */
enum class SwagLogoSize { SMALL, STANDARD, LARGE }

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
    /** Path 2 (24.2): defaults to STANDARD when personalized but unset. */
    val personalizationLogoSize: SwagLogoSize? = null,
)

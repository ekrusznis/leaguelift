package com.rally26.store.application

import com.rally26.common.error.ValidationException
import com.rally26.store.domain.ProductStatus

/** Phase 24.1 product lifecycle rules kept pure so every caller gets the same behavior. */
object ProductLifecycle {
    fun requireTransition(
        current: ProductStatus,
        target: ProductStatus,
    ) {
        if (current == target) return
        if (current == ProductStatus.ARCHIVED && target != ProductStatus.DRAFT) {
            throw ValidationException("Restore an archived product to draft before activating it.")
        }
    }
}

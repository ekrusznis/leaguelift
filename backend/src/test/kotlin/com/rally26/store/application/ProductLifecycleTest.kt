package com.rally26.store.application

import com.rally26.common.error.ValidationException
import com.rally26.store.domain.ProductStatus
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProductLifecycleTest {
    @Test
    fun `archived product restores to draft`() {
        ProductLifecycle.requireTransition(ProductStatus.ARCHIVED, ProductStatus.DRAFT)
    }

    @Test
    fun `archived product cannot activate directly`() {
        assertFailsWith<ValidationException> {
            ProductLifecycle.requireTransition(ProductStatus.ARCHIVED, ProductStatus.ACTIVE)
        }
    }

    @Test
    fun `active and draft products may archive`() {
        ProductLifecycle.requireTransition(ProductStatus.ACTIVE, ProductStatus.ARCHIVED)
        ProductLifecycle.requireTransition(ProductStatus.DRAFT, ProductStatus.ARCHIVED)
    }
}

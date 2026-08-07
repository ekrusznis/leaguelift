package com.rally26.integration.printify.application

import com.rally26.organization.domain.Organization
import com.rally26.organization.domain.OrganizationStatus
import com.rally26.organization.domain.OrganizationType
import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.store.domain.Store
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.StoreRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintifyOwnershipPrefixServiceTest {
    private val organizationRepository = mockk<OrganizationRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val service = PrintifyOwnershipPrefixService(organizationRepository, storeRepository)

    private val orgId = UUID.randomUUID()
    private val storeId = UUID.randomUUID()

    private fun organization() =
        Organization(
            id = orgId,
            name = "Riverside Soccer",
            slug = "riverside-soccer",
            organizationType = OrganizationType.RECREATIONAL_LEAGUE,
            status = OrganizationStatus.ACTIVE,
            sports = listOf("Soccer"),
            contactEmail = null,
            contactPhone = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun store() =
        Store(
            id = storeId,
            organizationId = orgId,
            teamId = null,
            name = "Spring Store",
            slug = "spring-store",
            status = StoreStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `prefixFor combines organization and store slugs`() {
        every { organizationRepository.findById(orgId) } returns organization()
        every { storeRepository.findById(storeId, orgId) } returns store()

        assertEquals("riverside-soccer/spring-store", service.prefixFor(orgId, storeId))
    }

    @Test
    fun `productTitle prefixes the product name in brackets`() {
        every { organizationRepository.findById(orgId) } returns organization()
        every { storeRepository.findById(storeId, orgId) } returns store()

        assertEquals(
            "[riverside-soccer/spring-store] Team Hoodie",
            service.productTitle(orgId, storeId, "Team Hoodie"),
        )
    }

    @Test
    fun `productTitle truncates an excessively long product name`() {
        every { organizationRepository.findById(orgId) } returns organization()
        every { storeRepository.findById(storeId, orgId) } returns store()

        val longName = "A".repeat(400)
        val result = service.productTitle(orgId, storeId, longName)

        assertEquals(250, result.length)
    }

    @Test
    fun `orderExternalId appends the order id after the prefix`() {
        every { organizationRepository.findById(orgId) } returns organization()
        every { storeRepository.findById(storeId, orgId) } returns store()
        val orderId = UUID.randomUUID()

        assertEquals("riverside-soccer/spring-store:$orderId", service.orderExternalId(orgId, storeId, orderId))
    }

    @Test
    fun `parseOrderId round-trips a real orderExternalId value`() {
        every { organizationRepository.findById(orgId) } returns organization()
        every { storeRepository.findById(storeId, orgId) } returns store()
        val orderId = UUID.randomUUID()

        val externalId = service.orderExternalId(orgId, storeId, orderId)

        assertEquals(orderId, service.parseOrderId(externalId))
    }

    @Test
    fun `parseOrderId returns null for a value that is not a UUID`() {
        assertNull(service.parseOrderId("not-a-uuid"))
    }
}

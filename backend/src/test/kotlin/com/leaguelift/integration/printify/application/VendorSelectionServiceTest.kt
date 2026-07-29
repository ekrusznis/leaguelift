package com.leaguelift.integration.printify.application

import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.integration.printify.infra.PrintifyCatalogClient
import com.leaguelift.integration.printify.infra.PrintifyLocation
import com.leaguelift.integration.printify.infra.PrintifyPrintProviderDetail
import com.leaguelift.integration.printify.infra.PrintifyPrintProviderSummary
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.client.ResourceAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VendorSelectionServiceTest {

	private val printifyCatalogClient = mockk<PrintifyCatalogClient>()
	private val service = VendorSelectionService(printifyCatalogClient)

	@Test
	fun `listUsPrintProviders keeps only providers located in the US`() {
		every { printifyCatalogClient.listPrintProviders(99L) } returns listOf(
			PrintifyPrintProviderSummary(1L, "US Provider", listOf("dtg")),
			PrintifyPrintProviderSummary(2L, "Canadian Provider", listOf("dtg")),
			PrintifyPrintProviderSummary(3L, "Another US Provider", null),
		)
		every { printifyCatalogClient.getPrintProviderLocation(1L) } returns
			PrintifyPrintProviderDetail(1L, "US Provider", PrintifyLocation("US", "CA", "Los Angeles", null))
		every { printifyCatalogClient.getPrintProviderLocation(2L) } returns
			PrintifyPrintProviderDetail(2L, "Canadian Provider", PrintifyLocation("CA", null, "Toronto", null))
		every { printifyCatalogClient.getPrintProviderLocation(3L) } returns
			PrintifyPrintProviderDetail(3L, "Another US Provider", PrintifyLocation("US", "NY", "New York", null))

		val result = service.listUsPrintProviders(99L)

		assertEquals(listOf(1L, 3L), result.map { it.id })
	}

	@Test
	fun `listUsPrintProviders excludes a provider with no location data at all`() {
		every { printifyCatalogClient.listPrintProviders(99L) } returns listOf(PrintifyPrintProviderSummary(1L, "Unknown", null))
		every { printifyCatalogClient.getPrintProviderLocation(1L) } returns PrintifyPrintProviderDetail(1L, "Unknown", null)

		val result = service.listUsPrintProviders(99L)

		assertEquals(emptyList(), result)
	}

	@Test
	fun `a Printify API failure is translated into a clean ServiceUnavailableException`() {
		every { printifyCatalogClient.listPrintProviders(99L) } throws ResourceAccessException("connection refused")

		assertFailsWith<ServiceUnavailableException> {
			service.listUsPrintProviders(99L)
		}
	}
}

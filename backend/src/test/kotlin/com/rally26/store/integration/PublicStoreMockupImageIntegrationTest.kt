package com.rally26.store.integration

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.store.application.StoreService
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.StoreStatus
import com.rally26.store.persistence.ProductRepository
import com.rally26.store.persistence.ProductVariantRepository
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-031: the public storefront's
 * `PublicProductVariantResponse` never included a Printify variant's real mockup
 * image, even though `ProductVariant.mockupFrontUrl` is already populated at
 * variant-creation time and already shown to org admins in `ProductManagementPanel`.
 * A shopper on the public store could never see a picture of any Printify-catalog
 * product — found live while investigating why the public storefront showed no
 * product images at all.
 */
class PublicStoreMockupImageIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var storeService: StoreService

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var productVariantRepository: ProductVariantRepository

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `the public store endpoint includes a Printify variant's real mockup image`() {
        val appUser =
            passwordAuthenticationService.register(
                "public-store-mockup-owner-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val owner: CurrentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val organization =
            organizationService.create(
                "Riverside Soccer",
                "riverside-soccer-mockup-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                owner,
            )
        val slug = "mockup-store-${System.nanoTime()}"
        val store = storeService.create(organization.id, null, "Mockup Store", slug, owner)
        storeService.updateStatus(organization.id, store.id, StoreStatus.ACTIVE, owner)

        val product =
            productRepository.insert(
                organization.id,
                store.id,
                "Riverside Youth Hoodie",
                null,
                CatalogSource.PRINTIFY,
                null,
                123L,
                "front",
            )
        productRepository.updateStatus(product.id, organization.id, ProductStatus.ACTIVE)

        productVariantRepository.insertPrintify(
            organization.id,
            product.id,
            "Navy / M",
            1L,
            1L,
            "USD",
            2000L,
            4000L,
            mockupFrontUrl = "https://images.printify.com/mockup/navy-hoodie-front.png",
        )

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/public/stores/$slug"))
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(
            response.body().contains("https://images.printify.com/mockup/navy-hoodie-front.png"),
            "expected the variant's real Printify mockup image in the public response: ${response.body()}",
        )
    }
}

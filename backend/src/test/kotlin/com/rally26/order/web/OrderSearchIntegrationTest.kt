package com.rally26.order.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.order.persistence.OrderRepository
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.store.application.StoreService
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-025: `GET .../stores/{storeId}/orders/search`
 * (what `frontend/src/features/store/searchApi.ts`'s `useOrderSearch` has always called
 * to power the "Orders and fulfillment" panel) had no backend mapping at all — only the
 * plain, unfiltered `GET .../stores/{storeId}/orders` existed. Every request 404'd
 * ("Could not load orders." live in the browser, confirmed via direct curl), same class
 * of bug as LR-016/018/020. Exercises the real HTTP/routing pipeline against real
 * Postgres end-to-end, including the fulfillment LEFT JOIN.
 */
class OrderSearchIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var storeService: StoreService

    @Autowired
    lateinit var orderRepository: OrderRepository

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedStore(
        val token: String,
        val organizationId: java.util.UUID,
        val storeId: java.util.UUID,
    )

    private fun authedStore(label: String): AuthedStore {
        val appUser =
            passwordAuthenticationService.register(
                "order-search-$label-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser: CurrentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Order Search Test Org $label",
                "order-search-org-$label-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        val store =
            storeService.create(
                organization.id,
                null,
                "Order Search Store $label",
                "order-search-store-$label-${System.nanoTime()}",
                currentUser,
            )
        return AuthedStore(token.accessToken, organization.id, store.id)
    }

    private fun searchOrders(
        authed: AuthedStore,
        query: String,
    ): HttpResponse<String> {
        val uri = "http://localhost:$port/api/v1/organizations/${authed.organizationId}/stores/${authed.storeId}/orders/search?$query"
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer ${authed.token}")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with a confirmed order, not a 404`() {
        val authed = authedStore("basic")
        val pending =
            orderRepository.insertOfflinePending(
                authed.organizationId,
                authed.storeId,
                "USD",
                "Jamie Rivera",
                "jamie@example.com",
                null,
            )
        orderRepository.markOfflineConfirmed(pending.id, Instant.now())

        val response = searchOrders(authed, "page=0&size=25&sort=NEWEST")

        assertEquals(200, response.statusCode(), "expected a real search response, not a 404: ${response.body()}")
        assertTrue(response.body().contains("Jamie Rivera"))
        assertTrue(
            response.body().contains("\"fulfillmentStatus\":null"),
            "no fulfillment row yet, so this must be null not absent/erroring",
        )
    }

    @Test
    fun `search keyword filters by supporter name`() {
        val authed = authedStore("keyword")
        val match =
            orderRepository.insertOfflinePending(
                authed.organizationId,
                authed.storeId,
                "USD",
                "Alex Rivera",
                "alex@example.com",
                null,
            )
        orderRepository.markOfflineConfirmed(match.id, Instant.now())
        val other =
            orderRepository.insertOfflinePending(
                authed.organizationId,
                authed.storeId,
                "USD",
                "Sam Chen",
                "sam@example.com",
                null,
            )
        orderRepository.markOfflineConfirmed(other.id, Instant.now())

        val response = searchOrders(authed, "page=0&size=25&sort=NEWEST&q=rivera")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Alex Rivera"))
        assertTrue(!response.body().contains("Sam Chen"))
    }
}

package com.rally26.common.error

import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Repro/fix test for DESIGN-DOC.md section 14.1's known bug 2: a request body that
 * Jackson cannot bind (missing required field, wrong type, malformed JSON) was falling
 * through to [GlobalExceptionHandler.handleUnexpected] as a bare 500 instead of a 400,
 * because there was no [org.springframework.http.converter.HttpMessageNotReadableException]
 * handler. This test exercises the real HTTP/Jackson/exception-handler pipeline
 * end-to-end (unlike the rest of this codebase's integration tests, which call
 * application services directly) — that pipeline is exactly what was broken. Uses a
 * plain JDK HttpClient rather than TestRestTemplate, which isn't on this project's
 * Spring Boot 4.1 test classpath.
 */
class GlobalExceptionHandlerIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val bearerToken: String,
        val organizationId: UUID,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "exception-handler-${System.nanoTime()}@example.com",
                "password1234",
                "Test User",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Exception Handler Test Org",
                "exception-handler-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        return AuthedOrg(token.accessToken, organization.id)
    }

    private fun postFeeTemplate(
        organizationId: UUID,
        bearerToken: String,
        jsonBody: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/fee-templates"))
                .header("Authorization", "Bearer $bearerToken")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `a request body Jackson cannot bind returns 400 with the standard error envelope, not 500`() {
        val (token, organizationId) = authedOrg()

        // amountMinor is declared as a non-nullable Long with no default and @NotNull —
        // omitting it entirely is a body Jackson cannot bind (not a Bean Validation
        // failure, since validation never runs if construction itself fails).
        val response = postFeeTemplate(organizationId, token, """{"name":"Spring Registration"}""")

        assertEquals(400, response.statusCode())
        val body = response.body()
        assertNotEquals("INTERNAL_ERROR", extractCode(body), "must not fall through to the generic 500 handler")
        assert(!body.contains("Exception")) { "error body must never leak an internal exception class name: $body" }
        assert(!body.contains("com.fasterxml") && !body.contains("com.rally26")) {
            "error body must never leak internal package/class details: $body"
        }
    }

    @Test
    fun `an omitted optional field with a Kotlin default is accepted, not rejected as malformed`() {
        val (token, organizationId) = authedOrg()

        // currency has a Kotlin default of "USD" (CreateFeeTemplateRequest.currency) —
        // omitting it is valid input, not a malformed body, and must not 400/500.
        val response = postFeeTemplate(organizationId, token, """{"name":"Spring Registration","amountMinor":15000}""")

        assertEquals(
            201,
            response.statusCode(),
            "omitting a field with a Kotlin default should use that default, not fail: ${response.body()}",
        )
        assert(response.body().contains("\"currency\":\"USD\"")) { "expected the Kotlin default to be applied: ${response.body()}" }
    }

    private fun extractCode(body: String): String? = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
}

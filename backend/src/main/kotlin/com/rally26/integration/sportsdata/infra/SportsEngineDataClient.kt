package com.rally26.integration.sportsdata.infra

import com.fasterxml.jackson.annotation.JsonProperty
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.sportsdata.domain.ProviderEligibilityCapability
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

private val log = LoggerFactory.getLogger(SportsEngineDataClient::class.java)

/** SportsEngine's real, confirmed GraphQL endpoint (help.sportsengine.com article 8225304). */
private const val SPORTSENGINE_GRAPHQL_URL = "https://api.sportsengine.com/graphql"

private data class GraphQlRequest(
    val query: String,
)

/**
 * Real HTTP transport for SportsEngine's GraphQL API — genuinely posts to the
 * confirmed endpoint with the caller's bearer token. **The query below is
 * best-effort, not schema-verified**: SportsEngine's confirmed entity list is
 * Organizations/Profiles/Teams/Events/Registrations (help.sportsengine.com
 * article 8225304), and Relay-style `edges { node { ... } }` connections are a
 * reasonable default for a GraphQL API of this era, but the exact field names
 * (`id`/`name` are near-universal, everything else is a guess) were not checked
 * against the live schema at `dev.sportsengine.com/explorer` (auth-gated, not
 * reachable during this research pass). **Confirm and adjust field selections
 * against the real schema explorer before the first live activation.**
 */
@Component
class SportsEngineDataClient {
    private val restClient = RestClient.create()

    fun fetchSnapshot(accessToken: String): List<SportsDataExternalRecord> {
        val response =
            try {
                restClient
                    .post()
                    .uri(SPORTSENGINE_GRAPHQL_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(GraphQlRequest(QUERY))
                    .retrieve()
                    .body(SportsEngineGraphQlResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("SportsEngine GraphQL fetch failed: {}", ex.message)
                throw ServiceUnavailableException("SPORTSENGINE_FETCH_FAILED", "SportsEngine could not be reached or rejected the request.")
            }
        val data = response?.data ?: throw ServiceUnavailableException("SPORTSENGINE_FETCH_FAILED", "SportsEngine returned no data.")
        val records = mutableListOf<SportsDataExternalRecord>()
        data.organizations?.edges.orEmpty().forEach { edge ->
            val node = edge.node ?: return@forEach
            records += SportsDataExternalRecord(SportsDataEntityType.ORGANIZATION, node.id, null, node.name, emptyMap())
        }
        data.teams?.edges.orEmpty().forEach { edge ->
            val node = edge.node ?: return@forEach
            records +=
                SportsDataExternalRecord(
                    SportsDataEntityType.TEAM,
                    node.id,
                    node.organizationId,
                    node.name,
                    mapOfNotNullValues("season" to node.season),
                )
        }
        data.events?.edges.orEmpty().forEach { edge ->
            val node = edge.node ?: return@forEach
            records +=
                SportsDataExternalRecord(
                    SportsDataEntityType.EVENT,
                    node.id,
                    node.teamId,
                    node.name,
                    mapOfNotNullValues("startAt" to node.startAt),
                )
        }
        data.registrations?.edges.orEmpty().forEach { edge ->
            val node = edge.node ?: return@forEach
            // A registration record is the closest confirmed SportsEngine entity to eligibility
            // evidence — DESIGN-DOC.md's `ELIGIBILITY_EVIDENCE` entity type (Phase 31 slice 31.3)
            // has no dedicated SportsEngine resource of its own to fetch from.
            records +=
                SportsDataExternalRecord(
                    SportsDataEntityType.ELIGIBILITY_EVIDENCE,
                    node.id,
                    node.profileId,
                    node.name,
                    mapOf("capability" to ProviderEligibilityCapability.REGISTRATION_STATUS_IMPORT.name),
                )
        }
        return records
    }

    private fun mapOfNotNullValues(vararg pairs: Pair<String, String?>): Map<String, String?> = pairs.filter { it.second != null }.toMap()

    private companion object {
        // Relay-style connections, near-universal id/name fields only — see class doc for why
        // deeper fields aren't included until the live schema is checked.
        const val QUERY = """
            query Rally26Snapshot {
              organizations { edges { node { id name } } }
              teams { edges { node { id name organizationId season } } }
              events { edges { node { id name teamId startAt } } }
              registrations { edges { node { id name profileId } } }
            }
        """
    }
}

private data class SportsEngineGraphQlResponse(
    val data: SportsEngineGraphQlData?,
)

private data class SportsEngineGraphQlData(
    val organizations: SportsEngineConnection<SportsEngineOrganizationNode>?,
    val teams: SportsEngineConnection<SportsEngineTeamNode>?,
    val events: SportsEngineConnection<SportsEngineEventNode>?,
    val registrations: SportsEngineConnection<SportsEngineRegistrationNode>?,
)

private data class SportsEngineConnection<T>(
    val edges: List<SportsEngineEdge<T>>?,
)

private data class SportsEngineEdge<T>(
    val node: T?,
)

private data class SportsEngineOrganizationNode(
    val id: String,
    val name: String?,
)

private data class SportsEngineTeamNode(
    val id: String,
    val name: String?,
    @JsonProperty("organizationId") val organizationId: String?,
    val season: String?,
)

private data class SportsEngineEventNode(
    val id: String,
    val name: String?,
    @JsonProperty("teamId") val teamId: String?,
    @JsonProperty("startAt") val startAt: String?,
)

private data class SportsEngineRegistrationNode(
    val id: String,
    val name: String?,
    @JsonProperty("profileId") val profileId: String?,
)

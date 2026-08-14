package com.rally26.integration.sportsdata.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

private val log = LoggerFactory.getLogger(TeamSnapDataClient::class.java)

/** TeamSnap's real, confirmed APIv3 base URL — from the official `teamsnap_rb` SDK's `DEFAULT_URL` constant. */
private const val TEAMSNAP_API_BASE_URL = "https://apiv3.teamsnap.com"

/**
 * Real HTTP transport for TeamSnap's hypermedia REST (Collection+JSON) API.
 * The **envelope parsing below is a real, published spec** (Collection+JSON,
 * `collection.items[].data[].{name,value}` — not TeamSnap-specific guesswork),
 * so this genuinely decodes whatever TeamSnap returns into a flat field map. What
 * is **not schema-verified**: the exact resource paths (`/teams`, `/members`,
 * `/events`, `/divisions` — inferred from TeamSnap's own help-center description
 * of what the API exposes: "listing teams... roster information with players and
 * coaches... team events... availability") and the exact field *names* inside
 * each item's `data` array (`id`/`name`/`team_id`-style keys are a reasonable
 * hypermedia-REST default, not confirmed). **Confirm both against the live API
 * before the first live activation** — TeamSnap's public docs pages 404'd/
 * redirected during this research pass, so nothing here was checked against a
 * real authenticated response.
 */
@Component
class TeamSnapDataClient {
    private val restClient = RestClient.create()

    fun fetchSnapshot(accessToken: String): List<SportsDataExternalRecord> {
        val records = mutableListOf<SportsDataExternalRecord>()
        fetchCollection("/teams", accessToken).forEach { fields ->
            records +=
                SportsDataExternalRecord(SportsDataEntityType.TEAM, fields.require("id"), fields["division_id"], fields["name"], fields)
        }
        fetchCollection("/members", accessToken).forEach { fields ->
            records +=
                SportsDataExternalRecord(
                    SportsDataEntityType.PARTICIPANT,
                    fields.require("id"),
                    fields["team_id"],
                    fields.fullName(),
                    fields,
                )
        }
        fetchCollection("/events", accessToken).forEach { fields ->
            records += SportsDataExternalRecord(SportsDataEntityType.EVENT, fields.require("id"), fields["team_id"], fields["name"], fields)
        }
        // TeamSnap has no dedicated eligibility/waiver resource confirmed this pass — roster
        // membership itself is the closest available signal, same limitation the pre-existing
        // scaffold fixtures already documented (see ProviderEligibilityCapabilities: TeamSnap
        // claims WAIVER_ACKNOWLEDGMENT_IMPORT, but this pass found no concrete resource to back
        // it with — a real activation needs to confirm which TeamSnap resource actually carries
        // waiver-acknowledgment state before this entity type can be emitted for real).
        return records
    }

    private fun fetchCollection(
        path: String,
        accessToken: String,
    ): List<Map<String, String?>> {
        val response =
            try {
                restClient
                    .get()
                    .uri("$TEAMSNAP_API_BASE_URL$path")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .header(HttpHeaders.ACCEPT, "application/vnd.collection+json")
                    .retrieve()
                    .body(CollectionJsonEnvelope::class.java)
            } catch (ex: RestClientException) {
                log.warn("TeamSnap fetch of {} failed: {}", path, ex.message)
                throw ServiceUnavailableException("TEAMSNAP_FETCH_FAILED", "TeamSnap could not be reached or rejected the request.")
            }
        val items = response?.collection?.items ?: throw ServiceUnavailableException("TEAMSNAP_FETCH_FAILED", "TeamSnap returned no data.")
        return items.map { item -> item.data.orEmpty().associate { it.name to it.value } }
    }

    private fun Map<String, String?>.require(key: String): String =
        this[key] ?: throw ServiceUnavailableException("TEAMSNAP_FETCH_FAILED", "TeamSnap returned an item with no \"$key\" field.")

    private fun Map<String, String?>.fullName(): String? =
        listOfNotNull(this["first_name"], this["last_name"]).joinToString(" ").ifBlank { this["name"] }
}

/** Real Collection+JSON envelope shape (RFC-draft spec, not TeamSnap-specific). */
private data class CollectionJsonEnvelope(
    val collection: CollectionJsonCollection?,
)

private data class CollectionJsonCollection(
    val items: List<CollectionJsonItem>?,
)

private data class CollectionJsonItem(
    val data: List<CollectionJsonField>?,
)

private data class CollectionJsonField(
    val name: String,
    val value: String?,
)

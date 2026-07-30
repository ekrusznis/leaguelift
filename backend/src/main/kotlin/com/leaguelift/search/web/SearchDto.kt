package com.leaguelift.search.web

import com.leaguelift.search.domain.SearchHit
import java.util.UUID

data class SearchHitResponse(
	val type: String,
	val id: UUID,
	val label: String,
	val subtitle: String?,
)

fun SearchHit.toResponse() = SearchHitResponse(type.name, id, label, subtitle)

data class SearchResponse(val items: List<SearchHitResponse>)

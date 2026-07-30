package com.leaguelift.search.domain

import java.util.UUID

enum class SearchResultType { TEAM, PARTICIPANT, HOUSEHOLD, ORGANIZATION }

data class SearchHit(
	val type: SearchResultType,
	val id: UUID,
	val label: String,
	val subtitle: String?,
)

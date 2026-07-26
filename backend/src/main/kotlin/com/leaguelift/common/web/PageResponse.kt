package com.leaguelift.common.web

/** Standard paginated response envelope (DESIGN-DOC.md section 13.3). */
data class PageResponse<T>(
	val items: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
)

data class PageRequest(
	val page: Int = 0,
	val size: Int = 20,
) {
	init {
		require(page >= 0) { "page must be >= 0" }
		require(size in 1..100) { "size must be between 1 and 100" }
	}

	val offset: Int get() = page * size
}

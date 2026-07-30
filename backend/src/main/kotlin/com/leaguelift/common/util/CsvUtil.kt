package com.leaguelift.common.util

/**
 * Shared by every CSV export (fee collections export, Phase 2 remainder; org reports,
 * Phase 9 slice 1, ADR-025) — previously duplicated privately in `FeeService`.
 */
object CsvUtil {

	fun escape(value: String): String =
		if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
			"\"${value.replace("\"", "\"\"")}\""
		} else {
			value
		}

	/** Minor currency units (e.g. cents) -> a plain decimal string, e.g. 12345 -> "123.45". */
	fun formatMinor(minor: Long): String {
		val whole = minor / 100
		val fraction = kotlin.math.abs(minor % 100)
		return "$whole.${fraction.toString().padStart(2, '0')}"
	}
}

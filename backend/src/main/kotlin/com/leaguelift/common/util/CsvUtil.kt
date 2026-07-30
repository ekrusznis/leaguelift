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

	/**
	 * A minimal RFC 4180 parser (event CSV import, Phase 12 slice 2, ADR-032) — quoted
	 * fields may contain commas, embedded newlines, and escaped (`""`) quotes. No
	 * library was added for this, same "plain implementation over a new dependency for
	 * one narrow need" call this codebase already made for ICS generation (ADR-028).
	 */
	fun parse(content: String): List<List<String>> {
		val rows = mutableListOf<List<String>>()
		var row = mutableListOf<String>()
		val field = StringBuilder()
		var inQuotes = false
		var i = 0
		while (i < content.length) {
			val c = content[i]
			if (inQuotes) {
				when {
					c == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
						field.append('"')
						i++
					}
					c == '"' -> inQuotes = false
					else -> field.append(c)
				}
			} else {
				when (c) {
					'"' -> inQuotes = true
					',' -> {
						row.add(field.toString())
						field.clear()
					}
					'\r' -> {}
					'\n' -> {
						row.add(field.toString())
						field.clear()
						rows.add(row)
						row = mutableListOf()
					}
					else -> field.append(c)
				}
			}
			i++
		}
		if (field.isNotEmpty() || row.isNotEmpty()) {
			row.add(field.toString())
			rows.add(row)
		}
		return rows.filterNot { it.size == 1 && it[0].isBlank() }
	}
}

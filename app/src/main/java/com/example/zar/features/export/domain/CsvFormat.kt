package com.example.zar.features.export.domain

/** A finished CSV and how many data rows (not counting the header) it holds. */
data class CsvDocument(val text: String, val rowCount: Int)

/**
 * Minimal CSV writing for spreadsheets (RFC 4180).
 *
 * - Cells containing a comma, quote, carriage return or line feed are wrapped in quotes and any
 *   quote inside is doubled, so a note such as `Lunch, "Keells"` stays one cell.
 * - Rows end with CRLF, as the standard says.
 * - A text cell that begins with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading
 *   apostrophe, so a spreadsheet can never run it as a formula. Numbers and dates the app writes
 *   never start with those characters, so this only touches free text.
 */
object Csv {
    private const val LINE_END = "\r\n"
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    fun cell(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val safe = if (value.first() in FORMULA_STARTS) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun row(cells: List<String?>): String = cells.joinToString(",") { cell(it) }

    fun document(header: List<String>, rows: List<List<String?>>): CsvDocument {
        require(rows.all { it.size == header.size }) { "Every row needs ${header.size} cells" }
        val text = buildString {
            append(row(header)).append(LINE_END)
            rows.forEach { append(row(it)).append(LINE_END) }
        }
        return CsvDocument(text, rows.size)
    }
}

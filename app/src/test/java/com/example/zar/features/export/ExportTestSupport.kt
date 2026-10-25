package com.example.zar.features.export

import com.example.zar.core.domain.model.UserId
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.features.export.domain.model.ExportFile
import com.example.zar.features.export.domain.repository.ExportFileWriter
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A signed-in (or signed-out) user. */
class FakeSession(user: String? = "alice") : UserSessionProvider {
    private val state = MutableStateFlow(user?.let(::UserId))
    override fun currentUserId(): UserId? = state.value
    override fun observeUserId(): Flow<UserId?> = state
}

class RecordingWriter : ExportFileWriter {
    val written = mutableListOf<Pair<String, ExportFile>>()
    var failing = false
    /** The name the destination reports back; null means it reports none. */
    var savedName: String? = null

    /** When set, writing waits for it, so tests can look at the state mid-save. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun write(location: String, file: ExportFile): String? {
        gate?.await()
        if (failing) throw IOException("cannot write")
        written += location to file
        return savedName
    }
}

/**
 * A small RFC 4180 reader, so tests check what a spreadsheet would actually see (quotes, commas
 * and line breaks inside cells) instead of comparing raw text.
 */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<MutableList<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            !inQuotes && c == ',' -> { row += cell.toString(); cell.clear() }
            !inQuotes && c == '\r' && text.getOrNull(i + 1) == '\n' -> {
                row += cell.toString(); cell.clear()
                rows += row; row = mutableListOf(); i++
            }
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    return rows
}

/** Rows as header-name to value maps. */
fun csvRecords(text: String): List<Map<String, String>> {
    val rows = parseCsv(text)
    val header = rows.first()
    return rows.drop(1).map { header.zip(it).toMap() }
}

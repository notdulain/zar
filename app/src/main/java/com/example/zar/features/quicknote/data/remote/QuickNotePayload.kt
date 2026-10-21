package com.example.zar.features.quicknote.data.remote

import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest

/** The wire request: the note, today's date and bare account facts, exactly as the service validates them. */
internal object QuickNotePayload {
    fun from(request: QuickNoteDraftRequest): Map<String, Any> = mapOf(
        "note" to request.note,
        "today" to request.today.toString(),
        "accounts" to request.accounts.map {
            mapOf(
                "id" to it.id.value,
                "name" to it.name,
                "type" to it.type.name,
                "currency" to it.currency.code,
            )
        },
    )
}

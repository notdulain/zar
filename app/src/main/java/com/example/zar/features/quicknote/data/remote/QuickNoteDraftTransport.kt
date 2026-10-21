package com.example.zar.features.quicknote.data.remote

/**
 * Sends one request to the draft service and returns its raw answer (a map as decoded from JSON).
 * Implementations throw `QuickNoteDraftError` for every service, auth and connectivity failure.
 */
interface QuickNoteDraftTransport {
    suspend fun createDraft(payload: Map<String, Any>): Any?
}

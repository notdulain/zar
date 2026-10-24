package com.example.zar.features.quicknote.data.remote

import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError.InputProblem

/**
 * Maps service reason codes to the existing typed UI errors. Shared by the HTTP Worker transport and
 * the original callable contract. Provider messages and other server text are never read.
 */
internal object CallableErrorMapper {

    fun map(code: String, reason: String?): QuickNoteDraftError = when (reason) {
        "UNAUTHENTICATED" -> QuickNoteDraftError.NotSignedIn()
        "APP_CHECK" -> QuickNoteDraftError.AppNotVerified()
        "INVALID_INPUT" -> QuickNoteDraftError.InvalidInput(InputProblem.REJECTED)
        "RATE_LIMITED" -> QuickNoteDraftError.RateLimited()
        "TIMEOUT" -> QuickNoteDraftError.TimedOut()
        "PROVIDER_UNAVAILABLE" -> QuickNoteDraftError.ServiceUnavailable()
        "BAD_RESPONSE" -> QuickNoteDraftError.InvalidResponse()
        else -> fromCode(code)
    }

    private fun fromCode(code: String): QuickNoteDraftError = when (code) {
        "UNAUTHENTICATED" -> QuickNoteDraftError.NotSignedIn()
        "PERMISSION_DENIED", "FAILED_PRECONDITION" -> QuickNoteDraftError.AppNotVerified()
        "INVALID_ARGUMENT" -> QuickNoteDraftError.InvalidInput(InputProblem.REJECTED)
        "RESOURCE_EXHAUSTED" -> QuickNoteDraftError.RateLimited()
        "DEADLINE_EXCEEDED" -> QuickNoteDraftError.TimedOut()
        // A bare UNAVAILABLE carries no service detail, so it is the network, not the provider.
        "UNAVAILABLE" -> QuickNoteDraftError.Offline()
        else -> QuickNoteDraftError.ServiceUnavailable()
    }
}

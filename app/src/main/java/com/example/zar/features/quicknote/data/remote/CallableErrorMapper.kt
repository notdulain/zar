package com.example.zar.features.quicknote.data.remote

import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError.InputProblem

/**
 * Maps a failed callable to a typed error. The service always sends a `reason` detail
 * (see functions/src/errors.js); without it the failure came from Firebase itself (no network, App Check,
 * an expired sign-in) and the Firebase error code decides. Provider or server text is never read.
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

package com.example.zar.features.quicknote.domain.model

/**
 * Why a draft could not be generated. Every failure leaves manual entry available: the caller still holds the
 * user's text. Messages are fixed text and never contain the note, accounts or provider output.
 * [isRetriable] says whether trying Interpret again could work.
 */
sealed class QuickNoteDraftError(message: String, val isRetriable: Boolean) : Exception(message) {

    enum class InputProblem { BLANK, TOO_LONG, REJECTED }

    class InvalidInput(val problem: InputProblem) : QuickNoteDraftError("The note cannot be interpreted", false)
    class NotSignedIn : QuickNoteDraftError("Sign in to interpret notes", false)
    class AppNotVerified : QuickNoteDraftError("This app could not be verified", false)
    class Offline : QuickNoteDraftError("No connection", true)
    class TimedOut : QuickNoteDraftError("Interpreting took too long", true)
    class RateLimited : QuickNoteDraftError("Too many requests", true)
    class ServiceUnavailable : QuickNoteDraftError("Interpreting is unavailable", true)
    class InvalidResponse : QuickNoteDraftError("The note could not be interpreted", true)
}

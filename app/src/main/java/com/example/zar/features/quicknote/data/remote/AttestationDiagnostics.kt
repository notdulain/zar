package com.example.zar.features.quicknote.data.remote

import com.google.firebase.FirebaseNetworkException
import java.io.IOException

/**
 * Why the app could not obtain an App Check token, or why the Worker refused the one it sent. "This app could not be
 * verified" covers all of these, so each is classified here and written to Logcat (tag `ZarQuickNote`) as a category and an
 * action only. Nothing the SDK returns is logged: no token, no debug secret, no response body.
 */
internal enum class AttestationFailure(val action: String) {
    /** No Firebase app: the build had no google-services.json, so no App Check provider was installed. */
    NO_PROVIDER("Build has no Firebase configuration, so no App Check provider was installed."),

    /** The device could not reach Firebase. */
    NETWORK("Could not reach Firebase App Check. Check the connection."),

    /** The SDK is backing off after earlier failures ("Too many attempts"). Fix the cause, then restart the app. */
    THROTTLED("App Check is backing off after earlier failures. Fix the cause, then force-stop and reopen the app."),

    /**
     * Firebase answered 401/403 to the token exchange. For the debug provider this is an unregistered debug secret: register
     * this install's secret under Firebase console > App Check > Manage debug tokens (app 1:588119468948:android:61ae9f5e46dc9cf09bbd81).
     * It also covers an App Check API that is disabled, or an Android app that is not registered for App Check.
     */
    EXCHANGE_REFUSED("Firebase refused to issue a token (HTTP 401/403): the debug secret is not registered, or the App Check API or app registration is missing."),

    /** Firebase answered 400 to the exchange: the API key, project or app ID in google-services.json is not accepted. */
    CONFIG_REJECTED("Firebase rejected the app configuration (HTTP 400): check google-services.json against the Firebase project."),

    /** The Worker returned APP_CHECK for a token that was freshly issued: the Worker's project number or app ID differ from the app's. */
    WORKER_REFUSED("The Worker refused a fresh App Check token: its FIREBASE_PROJECT_NUMBER or FIREBASE_APP_ID does not match this app."),

    /** The Worker returned UNAUTHENTICATED for a fresh Firebase ID token. */
    SESSION_REFUSED("The Worker refused a fresh Firebase sign-in token: sign out and back in."),

    UNKNOWN("App Check failed for a reason the SDK did not describe."),
}

internal object AttestationDiagnostics {
    private val httpCode = Regex("""code:\s*(\d{3})""")

    /** Classifies a failure of `FirebaseAppCheck.getAppCheckToken`. The exception is only inspected, never logged. */
    fun classify(error: Throwable): AttestationFailure {
        val message = error.message.orEmpty()
        return when {
            error is FirebaseNetworkException || error is IOException -> AttestationFailure.NETWORK
            error is IllegalStateException && message.contains("FirebaseApp", ignoreCase = true) -> AttestationFailure.NO_PROVIDER
            message.contains("Too many attempts", ignoreCase = true) -> AttestationFailure.THROTTLED
            else -> when (httpCode.find(message)?.groupValues?.get(1)?.toIntOrNull()) {
                401, 403 -> AttestationFailure.EXCHANGE_REFUSED
                400 -> AttestationFailure.CONFIG_REJECTED
                429 -> AttestationFailure.THROTTLED
                else -> AttestationFailure.UNKNOWN
            }
        }
    }
}

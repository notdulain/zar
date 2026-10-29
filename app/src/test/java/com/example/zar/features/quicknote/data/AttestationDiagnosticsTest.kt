package com.example.zar.features.quicknote.data

import com.example.zar.features.quicknote.data.remote.AttestationDiagnostics
import com.example.zar.features.quicknote.data.remote.AttestationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** App Check token failures, as the SDK words them, sorted into the few causes that have different fixes. */
class AttestationDiagnosticsTest {
    private fun classify(message: String) = AttestationDiagnostics.classify(RuntimeException(message))

    @Test fun anUnregisteredDebugSecretIsAnExchangeRefusal() {
        assertEquals(AttestationFailure.EXCHANGE_REFUSED, classify("Error returned from API. code: 403 body: App attestation failed."))
        assertEquals(AttestationFailure.EXCHANGE_REFUSED, classify("Error returned from API. code: 401 body: x"))
    }

    @Test fun aRejectedConfigurationIsNotTheSameAsARefusedSecret() {
        assertEquals(AttestationFailure.CONFIG_REJECTED, classify("Error returned from API. code: 400 body: API key not valid"))
    }

    @Test fun backoffIsRecognisedSoTheFixIsToRestartAfterRegistering() {
        assertEquals(AttestationFailure.THROTTLED, classify("Too many attempts."))
        assertEquals(AttestationFailure.THROTTLED, classify("Error returned from API. code: 429 body: quota"))
    }

    @Test fun networkProblemsAreNotAttestationProblems() {
        assertEquals(AttestationFailure.NETWORK, AttestationDiagnostics.classify(java.io.IOException("reset")))
    }

    @Test fun anythingElseIsUnknownAndNeverEchoesTheSdkText() {
        assertEquals(AttestationFailure.UNKNOWN, classify("Unknown error generating App Check token"))
        assertFalse(AttestationFailure.entries.any { it.action.contains("body:") })
    }
}

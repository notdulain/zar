package com.example.zar.features.quicknote.data

import com.example.zar.features.quicknote.data.remote.CallableErrorMapper
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import kotlin.reflect.KClass
import org.junit.Assert.assertTrue
import org.junit.Test

class CallableErrorMapperTest {
    private fun check(code: String, reason: String?, expected: KClass<out QuickNoteDraftError>) {
        val error = CallableErrorMapper.map(code, reason)
        assertTrue("$code/$reason -> ${error::class.simpleName}", expected.isInstance(error))
    }

    @Test fun serviceReasonsWinOverTheTransportCode() {
        check("UNAUTHENTICATED", "UNAUTHENTICATED", QuickNoteDraftError.NotSignedIn::class)
        check("FAILED_PRECONDITION", "APP_CHECK", QuickNoteDraftError.AppNotVerified::class)
        check("INVALID_ARGUMENT", "INVALID_INPUT", QuickNoteDraftError.InvalidInput::class)
        check("RESOURCE_EXHAUSTED", "RATE_LIMITED", QuickNoteDraftError.RateLimited::class)
        check("DEADLINE_EXCEEDED", "TIMEOUT", QuickNoteDraftError.TimedOut::class)
        check("UNAVAILABLE", "PROVIDER_UNAVAILABLE", QuickNoteDraftError.ServiceUnavailable::class)
        check("INTERNAL", "BAD_RESPONSE", QuickNoteDraftError.InvalidResponse::class)
    }

    @Test fun firebaseOnlyFailuresMapByCode() {
        check("UNAUTHENTICATED", null, QuickNoteDraftError.NotSignedIn::class)
        check("PERMISSION_DENIED", null, QuickNoteDraftError.AppNotVerified::class)
        check("UNAVAILABLE", null, QuickNoteDraftError.Offline::class)
        check("DEADLINE_EXCEEDED", null, QuickNoteDraftError.TimedOut::class)
        check("RESOURCE_EXHAUSTED", null, QuickNoteDraftError.RateLimited::class)
        check("INTERNAL", null, QuickNoteDraftError.ServiceUnavailable::class)
        check("NOT_FOUND", null, QuickNoteDraftError.ServiceUnavailable::class)
    }

    @Test fun unknownReasonFallsBackToTheCode() {
        check("UNAVAILABLE", "SOMETHING_NEW", QuickNoteDraftError.Offline::class)
    }
}

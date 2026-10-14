package com.example.zar.features.auth.presentation.viewmodel

import com.example.zar.features.auth.domain.model.AuthError
import kotlin.coroutines.cancellation.CancellationException

/** Runs an auth call and turns any failure into an AuthError the UI can explain. */
internal inline fun <T> runCatchingAuth(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: AuthError) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(AuthError.Unknown(e))
}

internal fun <T> Result<T>.onAuthFailure(action: (AuthError) -> Unit): Result<T> {
    exceptionOrNull()?.let { action(it as? AuthError ?: AuthError.Unknown(it)) }
    return this
}

package com.example.zar.features.transactions.data.category

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Keeps a live read alive across failures. When [source] fails, [degraded] is emitted (given the last value read,
 * if any) and [source] is subscribed again after [retryDelayMillis]. A normal completion ends the flow; cancellation
 * always propagates.
 *
 * This is what lets a Firestore listener error surface as a stale-but-usable list instead of dropping the user's
 * own data: the last value is remembered and re-emitted, and the listener is re-registered.
 */
internal fun <T> retrying(
    retryDelayMillis: Long,
    source: () -> Flow<T>,
    degraded: (last: T?) -> T? = { null },
): Flow<T> = flow {
    var last: T? = null
    while (true) {
        try {
            source().collect { value ->
                last = value
                emit(value)
            }
            return@flow
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            degraded(last)?.let { emit(it) }
            delay(retryDelayMillis)
        }
    }
}

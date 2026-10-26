package com.example.zar.features.transactions.data.category

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryingFlowTest {

    @Test
    fun keepsTheLastValueAndRetriesAfterAFailure() = runTest {
        var attempts = 0
        val source: () -> Flow<String> = {
            attempts++
            flow {
                if (attempts == 1) throw IllegalStateException("listener failed")
                emit("loaded")
            }
        }
        val values = retrying(retryDelayMillis = 1, source = source, degraded = { last -> "$last (stale)" })
            .take(2)
            .toList()
        assertEquals(listOf("null (stale)", "loaded"), values)
        assertEquals(2, attempts)
    }

    @Test
    fun aFirstFailureSurfacesDegradedWithoutAnyLastValue() = runTest {
        var attempts = 0
        val values = retrying(
            retryDelayMillis = 1,
            source = {
                flow {
                    attempts++
                    if (attempts < 2) throw IllegalStateException("down") else emit("ok")
                }
            },
            degraded = { last -> last ?: "degraded" },
        ).take(2).toList()
        assertEquals(listOf("degraded", "ok"), values)
    }

    @Test
    fun aNormalCompletionEndsTheFlow() = runTest {
        val values = retrying<String>(retryDelayMillis = 1, source = { flow { emit("a"); emit("b") } }).toList()
        assertEquals(listOf("a", "b"), values)
    }

    @Test
    fun cancellationPropagatesInsteadOfRetrying() = runTest {
        var attempts = 0
        val values = retrying<String>(
            retryDelayMillis = 1,
            source = { flow { attempts++; emit("first"); throw IllegalStateException("boom") } },
        ).take(1).toList()
        assertEquals(listOf("first"), values)
        assertEquals(1, attempts)
    }
}

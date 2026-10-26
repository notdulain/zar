package com.example.zar.features.transactions

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.features.auth.IsolatedFirebaseApp
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * An offline custom category plus an expense that uses it must survive a restart and reach the server once, against
 * the local Auth/Firestore emulators (`-Pzar.useEmulators=true`, `firebase emulators:start --only auth,firestore`).
 *
 * [aRestartedClientKeepsOfflineWritesAndDeliversThemOnReconnect] closes the Firestore client and opens a new one on
 * the same persistent cache, all in one process. The two `process…` tests prove it across a real process death; they
 * only run when `tools/offline-restart-check.sh` passes `-e restartPhase 1|2 -e restartRun <hex>`, because the process
 * must be killed between them.
 */
class OfflineRestartTest {
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun aRestartedClientKeepsOfflineWritesAndDeliversThemOnReconnect(): Unit = runBlocking {
        val scenario = OfflineCategoryRun(UUID.randomUUID().toString().replace("-", "").take(12))
        val first = IsolatedFirebaseApp.open(scenario.appName)
        scenario.writeOffline(first)
        first.close()

        val offlineAgain = IsolatedFirebaseApp.open(scenario.appName, IsolatedFirebaseApp.UNREACHABLE_PORT)
        try { scenario.restartOffline(offlineAgain) } finally { offlineAgain.close() }

        val online = IsolatedFirebaseApp.open(scenario.appName)
        try { scenario.reconnect(online) } finally { online.close() }
    }

    /** Phase 1 of a process restart: write offline, then leave the client open and let the script kill the process. */
    @Test
    fun processRestartPhase1WritesOffline(): Unit = runBlocking {
        assumeTrue("run through tools/offline-restart-check.sh", args.getString("restartPhase") == "1")
        OfflineCategoryRun(requireNotNull(args.getString("restartRun"))).let { scenario ->
            scenario.writeOffline(IsolatedFirebaseApp.open(scenario.appName))
            Unit
        }
    }

    /** Phase 2, in a brand-new process: the writes come back from disk while offline, then reach the server. */
    @Test
    fun processRestartPhase2ReadsThenDeliversThem(): Unit = runBlocking {
        assumeTrue("run through tools/offline-restart-check.sh", args.getString("restartPhase") == "2")
        val scenario = OfflineCategoryRun(requireNotNull(args.getString("restartRun")))
        val offline = IsolatedFirebaseApp.open(scenario.appName, IsolatedFirebaseApp.UNREACHABLE_PORT)
        try { scenario.restartOffline(offline) } finally { offline.close() }
        val online = IsolatedFirebaseApp.open(scenario.appName)
        try { scenario.reconnect(online) } finally { online.close() }
    }
}

# Firestore SDK integration evidence (26 October 2026, sequential Sonnet 5.5 pass)

Run against the **local Firebase Emulator Suite** (`firebase emulators:start --only auth,firestore --project demo-zar`,
Android emulator reaches the host at 10.0.2.2) on **emulator Pixel_6a API 37**. Built with `-Pzar.useEmulators=true`.
No live project was touched.

## Firestore rules (`firestore-tests/rules.test.mjs`, real rules engine)

`node --test rules.test.mjs` against the running emulator: **25 tests, 25 pass, 0 fail** (the packaged `npm test` wraps
the same file in `emulators:exec`, which would collide with the emulator already holding port 8080).

## Android instrumented repository tests (real Firestore Android SDK)

All five classes in **one instrumentation process** (`am instrument -e class ...`): **15 tests, `OK`, 0 failures**.
13 execute fully; 2 (`processRestartPhase1/2`) skip by design in that run because they need a kill between them and
are run by `tools/offline-restart-check.sh` (below).

| Class | Tests |
| --- | --- |
| `core.data.LedgerWriteOutcomeTest` | 4 |
| `transactions.ExpenseCategoryRepositoryTest` | 5 (offline local-first + reconnect, **late server rejection reported**, retry of the same id, listener-error degrade/recover, account isolation) |
| `auth.UserScopedDataTest` | 1 |
| `core.data.TransferBatchTest` | 2 |
| `transactions.OfflineRestartTest` | 3 (client restart in process; process-restart phase 1 and phase 2) |

## Root cause of the earlier hang (corrected)

The previous note blamed `disableNetwork()`. Measured on the emulator, `db.disableNetwork().await()` **returns in
~60 ms** (`OfflineCategoryRun: disableNetwork returned in 58 ms`). The hang was the next line: the test called
`transactions.document("e1").set(expense).await()` **while the network was disabled**. A Firestore `set()` Task only
completes on the **server's acknowledgement**, which can never arrive offline, so `runBlocking` waited forever and
`enableNetwork()` in `finally` was never reached. The write itself is accepted locally at once.

Fix (`OfflineCategoryRun`): every step is bounded and names itself on a timeout (`step(...)`); an offline write is
issued but **not awaited**; the stages read the **local cache** (`Source.CACHE`) and `metadata.hasPendingWrites()`; the
Task is shown to be incomplete offline (`assertFalse(expenseWrite.isComplete)`), and the acknowledgement is awaited only
after reconnecting.

## The restart is real

- **Client restart in one process** (`aRestartedClientKeepsOfflineWritesAndDeliversThemOnReconnect`): write offline, prove
  the **server holds neither document** through a separate client, close the Firestore client and the app, reopen the
  **same app name** (same on-disk persistent cache) against an **unreachable** server port, read category + expense back
  from disk with pending writes still set, then reopen against the emulator, `waitForPendingWrites()`, and see exactly one
  category and the expense on the server.
- **Process restart** (`tools/offline-restart-check.sh`): phase 1 writes offline and leaves; the script runs
  `am force-stop`; phase 2 runs in a **new process** and repeats the offline-read and reconnect stages. Log from the run:
  `writeOffline pid=6358` → `restartOffline pid=6473`, `reconnect pid=6473`, `PROCESS RESTART OK`.

## Cross-class `FirebaseApp was deleted` (corrected)

Reproduced: running `LedgerWriteOutcomeTest` then `ExpenseCategoryRepositoryTest` failed the second class's
`@BeforeClass` in `FirebaseAuth.useEmulator` with `IllegalStateException: FirebaseApp was deleted`. Cause: both created
apps with the **same fake API key**; Auth shares its backend client per API key, so the second app was handed the first
class's deleted app. Reopening a deleted app's name also crashed the process (two live heartbeat DataStores on the same
file, named from app name + application ID). `IsolatedFirebaseApp` gives every open a fresh API key and application ID
and the four classes now run together.

## Product review: late rejection of a custom category

`FirestoreExpenseCategoryRepository.create` waits 4 s and returns. An offline create that the server refuses on
reconnect was only logged while Firestore rolled the category back, so it **silently disappeared** from the picker while
the draft still pointed at it. Reproduced with an offline create of an 81-character name (rules bound is 80).

Fix: the repository reports such a rejection (`lateRejections`, never for a quick rejection that `create` already threw,
nor for a write that is merely queued). `ManualEntryViewModel` releases the selection and reopens **Add category** with
the typed name and the **same id** so it can be retried; the rest of the draft is untouched, and a category the person has
since replaced is left alone. Covered by `ManualEntryViewModelTest` (2 new) and
`ExpenseCategoryRepositoryTest.anOfflineCreateTheServerLaterRefusesIsReportedNotSilentlyDropped`.
Limit: if the person has already saved the expense under that category, the saved entry shows the generic
"unavailable" label; its id is kept.

## Not run

`SignInFlowTest`, `AllowanceRepositoryTest`, `AllowanceEvidenceTest`, `CaptureRepositoryTest`, `AutoCaptureTest`,
`CatchUpReminderWorkerTest` were not re-run; they do not touch the changed paths.

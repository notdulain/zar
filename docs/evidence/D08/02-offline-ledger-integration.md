# 2.1 Offline cross-feature ledger consistency

Acceptance check: *Test offline edits/reconnection, replay duplication, corrections and undo across feature boundaries.*

## What was added

`app/src/androidTest/java/com/example/zar/integration/`

| File | Role |
|---|---|
| `IntegrationHarness.kt` | One isolated Firebase client on the emulators (`demo-zar`, fresh anonymous user) wired with the **production** `FirestoreLedgerRepository`, `FirestoreAccountRepository`, `FirestoreDraftRepository`, `FirestoreExpectedPaymentRepository`, `FirestoreExpenseCategoryRepository` and the production domain use cases. Reads the server with `Source.SERVER`, re-projects it with `LedgerProjector`, and offers the shared balance/summary calculators. Reused by tasks 3.1 and 5.1. |
| `OfflineLedgerIntegrationTest.kt` | 9 emulator cases, listed below. Every step is bounded by `step(...)` so a hang fails with the step's name. |

No fake repositories, no mocked Firestore. Local acceptance is asserted while the network is disabled; the client is then reconnected, `waitForPendingWrites` awaited, and the server read directly. Balance comes from `AccountBalanceCalculator`, spending from `LedgerSummaryCalculator`, both on the local entries and on a ledger rebuilt from the server documents; the two must agree.

## Cases

| Test | Path covered | Server / projection assertions |
|---|---|---|
| `offlineSaveThenOfflineCorrectionReachTheServerOnceAndEveryProjectionAgrees` | Manual expense saved offline, corrected offline (LKR 500.00 → 650.00), reconnect | Original document equal to what was saved; exactly 1 correction, 0 voids; local = server entries; balance LKR 9,350.00; spending LKR 650.00; no rejected writes |
| `offlineCorrectionsOfASyncedExpenseApply…` | Two offline corrections (600 → 700) on a synced expense, then a replay of the first correction ID with different content | Both correction docs stored; stored first correction still 600.00; projected amount 700.00; balance 9,300.00; original unchanged |
| `aReplayedSaveWithTheSameIdKeepsTheOriginalAndIsCountedOnce` | Same-ID save with changed amount; offline double-save of a new ID | Server keeps the 500.00 original; 2 transaction docs; balance 9,300.00; spending 700.00; nothing reported as rejected |
| `anOfflineReplayWithChangedContentSettlesBack…` | Replay with different content made while offline, then reconnect | Local view returns to the original; balance 9,500.00; spending 500.00; no rejected-write report |
| `anOfflineUndoRemovesTheEntry…` | Undo offline; undo again; late correction written directly | Original kept; exactly 1 void and it is the first one; entry voided; balance back to 10,000.00; spending 0; `CorrectTransactionUseCase` refuses a correction of an undone entry |
| `aCapturedMessageLinkedOfflineAddsNoMoney…` | Production `CaptureTextUseCase` (parser → draft), offline `LinkCapturedDraftUseCase`, replay of link and capture, then undo of the linked manual entry | 1 transaction, 1 evidence link, draft `CONFIRMED`, link result `QUEUED` offline then `SAVED`; spending LKR 800.00 not 1,600.00; same text → same draft (`AlreadyReviewed`); after undo balance 10,000.00 and spending 0 |
| `aConfirmedCaptureCanBeCorrectedAndUndone…` | `ConfirmCapturedDraftUseCase` twice, offline `CorrectExpenseUseCase`, undo | 1 stored transaction; imported original (LKR 800.00) preserved; projected 850.00; balances 9,150.00 → 10,000.00 after undo |
| `aQuickNoteSavesOnlyTheConfirmedProposalOnce…` | Quick-note draft with an actual and an expected proposal; production `ConfirmQuickNoteProposalUseCase` offline twice, expected confirm, full replay, correction, undo | No money before confirm; only `draft-…-0` in transactions; expected payment stored separately and never moves balance/received; replay leaves 1 + 1; correction 1,250.00; undo restores 10,000.00 and keeps the expectation |
| `aTransferLegLinkedToAMessageIsNotDoubleCounted…` | Offline atomic transfer with fee, captured-message evidence linked to the out leg, undo of one leg | 3 records (out, in, fee), 1 evidence link; bank 4,970.00, cash 5,000.00; transfer is not spending (only the LKR 30.00 fee is); undo voids all 3, balances back to opening |

## Results

```
./gradlew :app:connectedDebugAndroidTest -Pzar.useEmulators=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.zar.integration.OfflineLedgerIntegrationTest
```

| Run | Device | Tests | Failed | Skipped |
|---|---|---|---|---|
| `OfflineLedgerIntegrationTest` | `Pixel_6a` AVD, Android 17 (API 37) | 9 | 0 | 0 |
| Regression of neighbours: `LedgerWriteOutcomeTest`, `TransferBatchTest`, `CaptureRepositoryTest`, `UserScopedDataTest` | same | 17 | 0 | 0 |

Firebase Auth + Firestore emulators `demo-zar`, started from this checkout so the rules under test are this branch's `firestore.rules`.

## Defects

None demonstrated. No production code changed in this task. The first emulator run failed for two test-side reasons, both fixed in the test: the app needs an ignored fake `google-services.json` to start at all, and a captured message carries its own bank date, so its fixture account needed an earlier opening date and a wide summary period.

## Observations and limits

- Captured-message confirmation (`confirmCapturedDraft`) is a server transaction by design ("confirming requires a connection", `FirestoreDraftRepository`). The test confirms online and corrects/undoes offline. Offline confirmation is not a supported path, so it is not tested as one.
- While a same-ID replay with different content is pending offline, the phone's view of that record is not asserted. What is asserted is the settled state after the server refuses it.
- `observeRejectedWrites()` covers transactions and transfers only. Corrections, voids and evidence links use `logFailure`; their replays are verified through server state instead.
- Clock: fixtures use the emulator's current Colombo date for manual entries and the fixed bank date `2026-10-11` for captured messages.
- Emulator tests do not prove live-service behaviour, App Check, or a physical device.

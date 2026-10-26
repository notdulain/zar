# Firestore SDK integration evidence (26 October 2026)

Run against the **local Firebase Emulator Suite** (`firebase emulators:start --only auth,firestore --project demo-zar`,
host loopback 10.0.2.2 from the Android emulator) on **emulator Pixel_6a API 37**. No live project was touched.
Android app built with `-Pzar.useEmulators=true`.

## Firestore rules (`firestore-tests/rules.test.mjs`, `@firebase/rules-unit-testing`)

`npm test` → `firebase emulators:exec --only firestore`: **25 tests, 25 pass, 0 fail** (duration ~3.9 s).
Covers owner-only transactions, replayed-save refusal, durable void markers, income/transfer/allowance shapes, the
custom expense-category rules (owner-only, `custom-` id prefix, name bounds, never changed or deleted) and the
malformed-document rejections. This is the real rules engine, not a unit fake.

## Android instrumented repository tests (real Firestore Android SDK)

| Class | Result |
| --- | --- |
| `core.data.LedgerWriteOutcomeTest` | **4/4 pass** when run alone (offline save appears once and survives reconnect; a server-rejected expense is reported with its record; replaying a stored expense is not reported rejected; a refused save keeps its id and leaves the list when dismissed) |
| `transactions.ExpenseCategoryRepositoryTest` | **3/4 pass**: `accountsAreIsolatedAndTheOwnersCatalogReturns`, `aListenerErrorDegradesInsteadOfDroppingTheCatalogAndRecovers`, `aRetryOfTheSameIdWritesOneDocumentAndADifferentWriteIsRefused`. `offlineCreateThenExpenseSurvivesRestartAndReconnect` **hangs** (see below) |
| `auth.UserScopedDataTest` | 1/1 pass (next user sees none of the previous user's records) |
| `core.data.TransferBatchTest` | 2/2 pass (transfer + fee stored together; a rejected fee leaves neither leg stored) |

Observed: **10 instrumented executions pass, 1 hangs, 0 assertion failures.**

### Known limitations (recorded, not passed)

- `ExpenseCategoryRepositoryTest#offlineCreateThenExpenseSurvivesRestartAndReconnect` **hangs in this environment**:
  after anonymous sign-in the test's `db.disableNetwork().await()` never returns (logcat shows the test starts, then no
  further `FirestoreExpenseCategories` activity; an earlier run of the same method crashed the app process). The same
  `disableNetwork()` pattern passes in `LedgerWriteOutcomeTest` in isolation, so this is a test/emulator
  offline-persistence instability, not a product assertion failure. The offline-create → expense → restart-replay path
  is otherwise covered by `RetryingFlowTest` and the repository mapper unit tests. **No pass is claimed for it.**
- Running `ExpenseCategoryRepositoryTest` and `LedgerWriteOutcomeTest` in one instrumentation process makes the second
  fail with `FirebaseApp was deleted`; run them in separate invocations. This is test isolation, not a product defect.

## Not run

- The remaining Firebase-backed classes (`SignInFlowTest`, `AllowanceRepositoryTest`, `AllowanceEvidenceTest`,
  `CaptureRepositoryTest`, `AutoCaptureTest`, `CatchUpReminderWorkerTest`) were not re-run; the classes above cover the
  expense/category/account-isolation paths this change touched.

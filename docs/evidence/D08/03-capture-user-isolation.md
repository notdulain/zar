# 3.1 Two-user capture isolation

Acceptance check: *Test two different users, sign-out and queued capture to prove no cross-user display or upload.*

## What was added

| File | Role |
|---|---|
| `app/src/androidTest/.../integration/CaptureUserIsolationTest.kt` | 2 emulator cases. Production `StageIncomingSmsUseCase` and `ProcessStagedSmsUseCase`, the production `RoomSmsStagingRepository` with the Android Keystore AES-GCM `SmsCipher` on a real Room file, and the production `FirestoreDraftRepository` / `FirestoreLedgerRepository` against the Auth + Firestore emulators (`demo-zar`). Two real email users on one phone. |
| `app/src/androidTest/.../integration/IntegrationHarness.kt` (`EmulatorAdmin`) | Reads a user's collection from the emulator with its rules-bypassing `Bearer owner` token, so "absent from B's account" is proven on the server and not inferred from B's own (rule-limited) reads. Emulator only. |
| `app/src/test/.../capture/domain/AutoCaptureTest.kt` | JVM regression for the defect below (session flips between the owner check and the save). |

### Case 1: queued capture, sign-out, switch and resume

1. A turns capture on; a bank message is staged. Result `STAGED`, one encrypted row bound to A.
2. A signs out. A new message is refused (`SIGNED_OUT`), the staging table still holds exactly the one row, and a processing pass reports `paused`, uploading nothing.
3. B signs in. B's visible drafts, ledger and capture queue are all empty. B's own message is refused (`OFF`: A owns capture). B's pass processes nothing. The server holds no drafts for A or B. A's row is still queued and still bound to A. B reading `users/{A}/drafts` gets `PERMISSION_DENIED`.
4. A signs back in. A's queue shows 1 waiting; the pass processes 1. The server holds exactly one draft, in A's account, with no raw text (`Dear Cardholder`, `has been authorised`, `rawText` all absent from the stored document). The staging row is deleted and the SQLite file never contains the plain text (checked after a WAL checkpoint). Staging and processing the same message again leaves one draft.
5. B signs in again and still sees nothing; B's server collection is empty.

### Case 2: controlled mid-flight switch

`RacingSession` wraps the real session and changes the signed-in user (sign out A, sign in B, awaited) immediately **before the n-th session read** of one `ProcessStagedSmsUseCase` pass. A dry run counts the reads in a pass (the test asserts at least 5), then the pass is repeated once per read position, plus one after the last read. This exercises: before the pass starts, between the ownership check and the repository's own read, inside the repository's transaction, and after commit. No sleeps or timing are involved. For each position the test asserts:

- A's draft is never present in B's server account;
- A's message is never lost (it is either on A's server account or still queued for A);
- no raw SMS text is uploaded;
- the user who is signed in afterwards sees only their own data;
- after A signs back in and the pass resumes, A has exactly one draft, B has none, and nothing stays queued.

## Defect found and fixed

**A user switch between `ProcessStagedSmsUseCase`'s owner check and the draft save moved A's captured draft into B's account and deleted A's message.**

- Reproduced deterministically by the case 2 run before the fix: `switch before read 3: A's draft was published into B's account`, `A's message was lost (not on the server, not queued)`, `after resume A has []`, `B ended with a draft`.
- Cause: the use case checked `session.currentUserId() == owner`, then `FirestoreDraftRepository.saveDraft` re-read the signed-in user and used *that* uid for the document path and its transaction guard. The guard compared the uid with itself, so a switch in the gap was accepted, the write went to `users/{B}/drafts` under B's own token, and the use case then cleared A's staged text.
- Fix (smallest change that binds the write to the owner the pass checked):
  - `DraftRepository.saveDraftFor(owner, draft)`: fails with `LedgerError.NotSignedIn` unless `owner` is still the signed-in user when the write starts and again inside the transaction.
  - `FirestoreDraftRepository` implements it by sharing the existing save path with the owner's uid instead of re-reading the session.
  - `CaptureTextUseCase(text, source, now, owner = null)` uses it when an owner is given, and `ProcessStagedSmsUseCase` passes `owner`. User-driven shares keep calling `saveDraft`.
  - The failure leaves the message queued (`left++`), so it is retried for its owner; the draft ID is derived from the text, so a retry cannot duplicate.
- Regression coverage: case 2 (every read position) and `AutoCaptureTest.aSwitchAfterTheOwnerCheckDoesNotMoveAlicesDraftIntoBobsAccountOrLoseTheMessage`. The five existing `DraftRepository` fakes gained a one-line `saveDraftFor`.

## Results

| Run | Device / runtime | Tests | Failed | Skipped |
|---|---|---|---|---|
| `CaptureUserIsolationTest`, before the fix | `Pixel_6a` AVD, Android 17 (API 37) | 2 | 1 (race case); the other failed on a test-side race in its own check, fixed in the test | 0 |
| `CaptureUserIsolationTest`, after the fix | same | 2 | 0 | 0 |
| Neighbours after the fix: `CaptureRepositoryTest`, instrumented `AutoCaptureTest`, `OfflineLedgerIntegrationTest`, `UserScopedDataTest` | same | 26 | 0 | 0 |
| JVM `features.capture.*`, `features.quicknote.*`, `architecture.*` | JDK 17, no emulator | 260 | 0 | 0 |

Commands:

```bash
./gradlew :app:connectedDebugAndroidTest -Pzar.useEmulators=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.zar.integration.CaptureUserIsolationTest
./gradlew :app:testDebugUnitTest --tests 'com.example.zar.features.capture.*' --tests 'com.example.zar.features.quicknote.*' --tests 'com.example.zar.architecture.*'
```

## Limits

- Queued capture is exercised through the use cases and repositories, not through the Android SMS broadcast receiver or WorkManager (the scheduler is a recording stub). The broadcast path and background scheduling are not covered here.
- Users are real emulator accounts on one `FirebaseAuth` instance, which is how the phone sees a switch. A second physical device is not involved.
- The race is injected at session reads, which are the only points where the processing path consults identity. Reads in code outside capture processing were not audited as part of this task.
- Emulator only: no live Auth/Firestore, App Check, or physical-device result.

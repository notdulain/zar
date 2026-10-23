# SCRUM-32 review evidence — 22 October 2026

The review integrates current main, including Bills navigation, without replacing the PR branch.

## Findings fixed

- Confirmation writes the immutable ledger record and closes its draft in one Firestore transaction. Server-confirmed replays and concurrent confirmations keep the first record. A rejected concurrent write is accepted only after reading the confirmed draft and existing record from the server.
- Draft creation returns an existing draft unchanged: repeating a message does not reset its capture time or reopen a confirmed/discarded draft.
- Draft mutations await the server. Save/Discard failures remain visible; retry keeps the original choices, and controls/Back are blocked during a save. Cached drafts can still be read offline; capture, confirmation and discard require a connection. The screen explains this limitation.
- Uncertain debits, withdrawals, transfers and credits require an explicit type. Future payments, wrong currencies and archived accounts are rejected; the account is rechecked inside the server transaction.
- Shared text uses a memory-only FIFO, including messages received while reading. Only the visible capture screen consumes it. Raw input is cleared after every read outcome; launch EXTRA_TEXT and ClipData are removed. Signing out clears queued text. Nothing saves raw text in SavedStateHandle or Firestore.
- Capture screens reuse Zar's Geist typography, cards, navy controls and neutral selections. More's dark hero uses light status glyphs.
- Status-only draft updates reuse the already validated, unchanged proposal data, avoiding the atomic request's rules-expression limit. Owner checks and final statuses stay enforced; invalid status, timestamp changes and raw-text additions are rejected.

## Design comparison

Read live Figma file `bBBJuQz1Cg8N34FcisKBs9`, Screens page `1:2`, and design context plus screenshot for More `343:5454` (Z107). There is no dedicated capture frame on this page. The added More row follows its existing component; capture pages use the app's established pushed-page scaffold, Geist text, white cards, neutral/navy controls and spacing. This is styling alignment, not a claim of pixel parity with a nonexistent capture frame.

## Validation

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: 642 unit tests pass; lint has 0 errors and 55 warnings; debug app and instrumentation APKs build.
- Firestore rules: 17 tests pass against local Firestore, including an atomic record + draft-status write and rejected schema/privacy mutations.
- Android emulator: 13 distinct instrumentation tests pass. Four run the actual FirestoreDraftRepository against local Auth/Firestore in project `demo-zar`: replay/discard, atomic confirmation/retry, stale account recovery and competing confirmations. Nine render and interact with the production composables using fixed data. A final UI rerun covers the screenshot polish.
- Unit regressions cover capture replay, terminal status, failed confirmation/retry, future dates, archived accounts, foreign draft sources, multiple shares while busy, inactive-screen handling and clearing text on sign-out.
- `git diff --check` passes.

## Screenshots

The ten numbered PNGs in this directory are unedited full-window screenshots captured from the final running Android app's production composables. They use fixed test data, not the live Firebase project. Screens 05 and 09 use the real local parser's credit and OTP outcomes. Server integration is verified separately by CaptureRepositoryTest.

1. Empty capture status, paste form and manual fallback.
2. Supported, needs-review and untested bank formats.
3. Parsed purchase waiting for review, with raw text cleared.
4. Purchase review: account, type, classification and actions.
5. Credit review requiring an explicit type.
6. Saving state: choices and actions disabled.
7. Save error: retry available, choices and discard locked.
8. Missing matching account with recovery instructions.
9. Ignored OTP and empty text input.
10. More entry point alongside the existing Bills feature.

No new picker or modal is introduced by these screens. The account/type controls are inline chips.

## Limits

No physical-device Messages share, full sign-in-to-navigation end-to-end test, live Firebase write, dark-mode, large-font or TalkBack run was performed in this review. Raw shared messages are intentionally not recovered after process death. Automatic SMS/background capture and editing captured amounts/dates are separate work; manual entry remains the fallback. Firestore rule changes are committed here; this review does not deploy production rules.

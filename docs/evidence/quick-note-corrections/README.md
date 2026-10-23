# Quick-note and Activity corrections — 24 October 2026

Screenshots are native production Compose screens on the Pixel 6a emulator, with fixed instrumentation-test
data. They show rendering and interaction; the displayed transactions are fixtures, not writes to a real account.

- `quicknote-date-time.png`: guessed date badge below the full date, plus a separate editable time.
- `quicknote-time-picker.png`: hour, minute, AM/PM, confirmation, cancellation and optional clearing.
- `activity-time.png`: a descriptive route title and recorded time in Activity detail.
- `activity-account-correction.png`: another account selected while retaining the stored currency.
- `activity-edit-time.png`: account and time corrected together in the expense editor.

Verification:

- Android: 848 JVM tests passed, and 51 instrumentation tests passed across `QuickNoteUiTest`,
  `ActivityScreensUiTest` and `IncomeCorrectionUiTest`.
- Firestore rules: 22 tests passed, including valid account changes, owner isolation, missing/archived/foreign
  currency rejection, time setting/clearing and immutable originals.
- Shared interpretation service: 50 tests passed. Worker: 37 tests and TypeScript check passed.
- Live OpenRouter fixture evaluation: 22/22 passed, including the descriptive Uber route, explicit 6:30 PM,
  and absent time remaining null. Results are in `tools/quicknote-eval/results/`.
- Domain tests verify both account balances, Activity filters/detail, correction history, immutable originals,
  linked-refund protection and duplicate-save retries.

The updated Worker was deployed as version `252e7f0d-96fe-4362-8ce7-4baa6b578c7d` and its health route returned
`{ "status": "ok", "version": 1 }`. Firestore rules were compiled and released to `zar-app-35602`.

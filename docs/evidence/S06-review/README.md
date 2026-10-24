# SCRUM-40 review evidence

Captured on 2026-10-24 from the running Android app on the Pixel_6a emulator, using the production Compose screens with fixed data in `CaptureUiTest`. These screenshots demonstrate native UI rendering and interactions, not a backend end-to-end flow. Dates and amounts are fixture data. Images are full-window, unedited captures from `UiAutomation.takeScreenshot()`.

## Screens

1. `01-possible-duplicate.png`: existing entry and the two duplicate answers.
2. `02-different-purchase.png`: separate-purchase review with Undo before saving.
3. `03-same-payment-undo.png`: same-payment answer inside the undo window.
4. `04-same-payment-saved.png`: completed link, with no Undo after writing.
5. `05-waiting-to-sync.png`: queued choice, waiting to sync.
6. `06-failed-link-retry.png`: failed choice with Try again and Undo my choice.
7. `07-transfer-picker.png`: unselected transfer and disabled Link action.
8. `08-transfer-selected.png`: selected transfer, explanation and enabled Link action.
9. `09-no-transfer.png`: no matching transfer and recovery instructions.
10. `10-transfer-undo.png`: linked-transfer answer inside the undo window.
11. `11-expired-message.png`: expired-message explanation and working manual-add action.
12. `12-credit-type-required.png`: credit requires an explicit type; income is not assumed.

The last image comes from `creditRequiresAnExplicitTypeAndShowsValidation`, whose capture name on the device is `05-choose-type.png`. It was copied without changing its pixels.

## Verification performed during review

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -Pzar.useEmulators=true` with JDK 21: successful.
- Unit results: 881 tests, 0 failures, 0 errors, 0 skipped, including six added ViewModel regressions.
- Android lint: 0 errors, 58 warnings; no findings on changed lines.
- Native instrumentation: `CaptureUiTest` (23), `AutoCaptureTest` (6), and `CaptureRepositoryTest` (10): all 39 passed.
- The two transfer-picker tests passed again after adjusting screenshot scroll positions. The first recapture attempt used the selected-state explanation for an unselected fixture; the selector was corrected to the actual unselected-state hint.
- Repository tests use real Android Firebase SDK repositories with local Auth/Firestore emulators (`demo-zar`) and the repository's Firestore rules. They cover atomic closure, no additional money posting, replay safety, direction persistence, offline queuing and acknowledgement, rejection after reconnect, and refusing to report another target's link as this choice's success.
- UI tests verify radio selection semantics, stale-selection disabling, legacy draft transfer selection, separate-purchase Undo, and expired-message manual recovery. Their callbacks use fixture data and do not claim backend integration.

## Design comparison and limits

Live Figma frames Z132–Z139 were read and visually inspected in file `bBBJuQz1Cg8N34FcisKBs9` (nodes `434:6307`, `434:6425`, `434:6514`, `434:6632`, `434:6748`, `434:6821`, `434:6910`, `434:6993`). The implementation reuses Zar's existing Compose scaffold, Geist typography, cards, buttons and chips. It is not pixel-identical: review choices and transfers share one scrolling page, the expiry notice lives on Capture sources, and queued writes offer Done while retry/undo are reserved for failed writes. Separate-purchase Undo is shown inline before saving.

No physical-device, dark-mode, enlarged-font or TalkBack run was performed. No live Firebase project was used. The expiry notice is fixture-driven; actual seven-day wall-clock ageing was not tested. Answers in the undo window and separate-purchase decisions are screen-local. Queued writes persist through Firestore; status observation and retention of a failed target operate while the review ViewModel is alive.

Original author-provided emulator evidence remains in `../S06/`; these captures supersede it for the current UI.

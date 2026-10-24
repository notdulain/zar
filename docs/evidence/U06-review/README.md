# Weekly allowance review evidence

Captured from the running Android app on 2026-10-24, Pixel_6a emulator. `AllowanceEvidenceTest` renders the production Home, Plans, allowance card and editor with fixed data. These full-window, unedited native screenshots are UI fixtures, not a backend end-to-end run. The week is 19–25 October 2026; example allowance LKR 8,000.

## States

- 01–03: Plans disabled, remaining, and overspent with all three coverage warnings.
- 04–05: Plans waiting to sync and rejected-sync recovery.
- 06–07: Home remaining and overspent with coverage, clear of the floating add button.
- 08: isolated Home card at 160 dp and 2× font scale. Amount and coverage text do not overflow; the navigation label is truncated at this extreme width.
- 09–11: empty editor, entered amount with two independent categories, and selected Sunday week start.
- 12–13: saving disables editing/actions; failed save preserves the entered choice and offers Save again.
- 14–15: Home disabled; switching the editor off retains the amount/categories/week start.

The emulator's floating handwriting toolbar remains visible beside the entered editor states. It does not cover field text, picker choices or actions. The isolated large-font fixture does not claim a complete large-font app walkthrough.

## Review verification

- JDK 21: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -Pzar.useEmulators=true` passed after the final production changes.
- Unit tests: 933, zero failures/errors/skips. Five review regressions cover live Colombo midnight/week rollover, duplicate-save/edit guards, late rejected-save recovery and an unvalued refund.
- Lint: zero errors, 58 warnings; none on changed lines.
- Firestore rules: all 21 tests passed against the local Firestore emulator using this branch's rules.
- Android: all 27 tests passed together: `AllowanceUiTest` 5, `AllowanceEvidenceTest` 5, `AllowanceRepositoryTest` 4, `HomeScreenUiTest` 7, `PlansScreenUiTest` 6. The affected capture methods passed again after fixture-only scroll/focus adjustments.
- Repository tests use the real Android Firebase SDK against local Auth/Firestore emulators (`demo-zar`). They verify no ledger posting, local queuing/reconnect acknowledgement, immediate and late server rejection, rollback, and user-scoped error state. They are distinct from the UI fixtures.
- `git diff --check` passed.

## Design and limits

Live Figma design context and screenshots were inspected for Home weekly limit `123:1794`, Home scrolled `123:1699`, and Plans `64:1814`, file `bBBJuQz1Cg8N34FcisKBs9`. Page metadata was checked for allowance designs. No filled allowance/editor frame was found. The implementation follows existing Zar Compose components and Geist typography. The populated Home card grows to preserve amounts/warnings; Plans uses the existing card/editor style rather than claiming pixel parity with a missing frame.

No live Firebase deployment or physical-device run was performed. The new `allowance/weekly` Firestore rule must be deployed before live allowance saves are accepted. The 2× font check covers the isolated Home card, not every screen. TalkBack was not run. The existing Plans dark-theme test passed, but no dedicated dark allowance flow was exercised. Midnight was verified with a controlled clock/date source, not an overnight device test. Firestore persists queued settings; failure diagnostics and last-submitted retry choices are in memory and do not survive process death.

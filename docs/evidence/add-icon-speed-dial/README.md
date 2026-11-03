# Add icon speed dial — review runtime evidence

Recaptured on 3 November 2026 after the PR review fixes, from the debug app on a physical Pixel 6a (1080 × 2400 pixels; existing density override 356, physical density 420; animator scale 1.0). No device settings were changed. Production source commit: `089c0a2e4de49a7af3a2397b4c280c9cfcd7354f`. APK SHA-256: `e733b0c63b91b1518cd897373488507a59217868b864fd820c874402468ff1e2`.

All ten images use synthetic instrumentation fixtures and production composables; no customer data, saved transactions or AI calls. These are native app captures, not Figma exports. Every final image was inspected for clipping, overlays and legibility. Dial captures wait for the native render-thread tap ripple to settle.

| Image | State |
| --- | --- |
| 01 | Fresh Home, closed dial, Light |
| 02 | Home, expanded icon actions, Light |
| 03 | Full navigation, expanded icon actions, Light |
| 04 | Full navigation, expanded icon actions, Dark |
| 05 | Manual entry dialog at rest, synthetic Cash account and LKR 1,250 |
| 06 | The same Manual entry dialog held partway through a downward pull |
| 07 | Quick note dialog at rest with a synthetic lunch note |
| 08 | The same Quick note dialog held partway through a downward pull |
| 09 | Manual entry date picker, with future dates disabled |
| 10 | Manual entry time picker |

01–04 use the committed `SpeedDialTest.homeFixtureRendersBothThemesAndChromeModes` fixture and actual HomeScreenContent with empty November data. 05–10 use production ManualEntryContent and QuickNoteContent in a real Compose Dialog with SheetBackHost. Each capture is taken from its Compose root, including the inline date/time picker. The temporary capture harness asserted that the actual sheets track a downward pull and that downward pulls on either picker leave it open. It was removed from app sources after capture. Persistent gesture regressions remain in SheetBackHostTest.

## Fresh review verification

These results were run in the isolated review worktree; they do not reuse the earlier author counts.

- Debug app and instrumentation APK assembly passed.
- `:app:testDebugUnitTest`: 1,297 tests passed, 0 failures/errors/skips. Includes a new regression for the centred Home row's direction under half-pixel rounding in LTR/RTL.
- Pixel 6a combined run: 36 tests passed — SpeedDialTest (6), SheetBackHostTest (8), QuickNoteUiTest (14), ZarScreenScaffoldChromeTest (1), NavRevealMotionTest (3), AddTransactionSessionUiTest (2), and temporary production-dialog capture cases (2).
- The new touch-during-exit regression first failed on the original PR head (0 pops instead of 1), then passed after the fix. Return springs remain interruptible; committed exits complete exactly once.
- Zero recomposition batches during 12 controlled dial spring frames. This verifies layer updates, not a real-time FPS guarantee.
- Live Figma review compared Home open (`734:21246`), full navigation Light (`734:21578`) and Dark (`734:22355`) with the running app captures. Action sizes/order, offsets, anchor rotation and palette follow those frames; page data is synthetic and differs from the design examples.
- `:app:lintDebug`: one pre-existing NonObservableLocale error at PlansHero.kt:213, also present in `main`. No lint errors in changed files.
- `git diff --check`: passed.

Not established: a real-time FPS guarantee, actual TalkBack/D-pad traversal, the full landscape/split-screen/large-font matrix, process death, or end-to-end live Firebase/AI behavior. Reduced and Instant policy were tested by injection without changing the phone's system animation setting.

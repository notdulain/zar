# Add icon speed dial — runtime evidence

Captured on 3 November 2026 from the debug app on a physical Pixel 6a (1080 × 2400 pixels; existing density override 356, physical density 420; animator scale 1.0). No device settings were changed. Production source commit: `0cc4313fba360f2bae0c0adb30a4184436d630fb`. APK SHA-256: `39ddeb096b52cb31f43c932e09566f4ed16eb0bca6c472e1e1529e90fb7948ba`.

All images use synthetic instrumentation fixtures and production composables; no customer data, saved transactions or AI calls. These are app captures, not Figma exports. Images were inspected after capture. Dial captures wait for the native render-thread tap ripple to settle.

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

01–04 use the committed `SpeedDialTest.homeFixtureRendersBothThemesAndChromeModes` fixture and actual HomeScreenContent. 05–08 use production ManualEntryContent and QuickNoteContent in a real Compose Dialog with SheetBackHost; the capture harness also asserted that the actual sheet tracked the pull. The capture-only harness was removed after use. Persistent gesture regressions are in SheetBackHostTest.

## Verification

- `:app:assembleDebug :app:testDebugUnitTest`: passed; 1296 unit tests, 0 failures, 0 errors.
- Final Pixel 6a run: SpeedDialTest and SheetBackHostTest, 13 tests passed. Includes dial state/routing callbacks, fixed nav bounds, rapid retargets, Reduced/Instant policy, no frame-by-frame chrome recomposition, slide-up entrance and focus gate, short-pull cancellation, downward dismissal, nested scrolling, and button/predictive Back.
- The two capture-only sheet cases and Home capture case passed (3 cases) before the final dial refresh.
- Existing QuickNoteUiTest: 14 passed in the preceding targeted run. Existing scaffold and nav-reveal tests: 4 passed earlier during implementation.
- `:app:lintDebug` remains blocked by the pre-existing NonObservableLocale error in PlansHero.kt:213. No lint errors were reported in changed files.
- `git diff --check`: passed.

Not established by these captures: a real-time FPS guarantee, actual TalkBack/D-pad traversal, the full landscape/split-screen/large-font matrix, process death, or end-to-end live Firebase/AI behavior. Reduced and Instant policy were tested by injection, without changing the phone's system animation setting.

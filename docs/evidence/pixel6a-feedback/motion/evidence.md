# Motion and high-refresh evidence

Branch `t3/resume-pixel6a-implementation` (consolidated recovery worker). Author: OpenCode DeepSeek V4.1 Flash
(authorized fallback). This file records exactly what was and was not measured; no physical or high-refresh claim is made.

## Top-card parallax coverage (implementation pass, 26 Oct 2026)

`Modifier.heroParallax` reads the hero's displacement in the layer phase (`graphicsLayer`) only, so scrolling never
recomposes a hero and never rebuilds a chart path. Fractions/caps are the shared `ZarMotion` tokens.

| Screen / card | Code owner | Scroll owner | Layers (fraction/cap) | Reduced motion |
| --- | --- | --- | --- | --- |
| Home Received hero | `HomeScreen.ReceivedHero` | `ScrollState` | background 0.20/24dp + text 0.06/8dp + chart 0.12/16dp | all at 0 |
| Activity hero | `ActivityScreen.ActivityHero` | `LazyListState` (hero = item 0) | background 0.20/24dp + text 0.06/8dp | all at 0 |
| Plans hero | `PlansScreen.GoalHero` (+ loading/error) | `ScrollState` | text 0.06/8dp + chart 0.12/16dp | all at 0 |
| More hero | `MoreScreen.MoreHero` | `ScrollState` | text 0.06/8dp | at 0 |
| Accounts header | `AccountsScreen` title block | `ScrollState` | text 0.06/8dp | at 0 |
| Account detail summary | `AccountDetailScreen.DetailContent` | `ScrollState` | text 0.06/8dp | at 0 |
| Received income summary | `IncomeListScreen.ReceivedHeader` | `LazyListState` | text 0.06/8dp | at 0 |
| Freelance owed | `FreelanceScreens.OwedHeader` | `LazyListState` | text 0.06/8dp | at 0 |
| Project summary | `FreelanceScreens.ProjectScreen` | `LazyListState` | text 0.06/8dp | at 0 |
| Milestone summary | `MilestoneScreen` | `ScrollState` | text 0.06/8dp | at 0 |
| Crypto realized | `CryptoScreens.CryptoContent` | `LazyListState` | text 0.06/8dp | at 0 |

Not applicable (documented, no hero/summary card over a scroll range): Transaction/refund detail, Needs review,
Catch-up/reminders, Settings, and the Add/category/Income sheet chrome (grabber, close, keyboard). Lazy heroes use a
stable saturated offset once the hero has left (`LazyListState.heroDisplacement()`), so reverse scroll re-enters
continuously; `HeroParallaxTest` covers rest, fractional, saturated and terminal cases. Motion is gated by
`MotionPolicy`; unit tests `MotionPolicyTest`, `NavCapsuleControllerTest`, `NavSessionTest`, `NavRouteMotionTest`,
`SelectionHapticsTest`, `HeroParallaxTest` pass in `:app:testDebugUnitTest` (1202 tests, 0 failures).

## High-refresh audit (source)

- **No fixed 60 Hz cap**: a search of `app/src/main/java` finds no `Choreographer`, `withFrameNanos`,
  `Surface.setFrameRate`, `Window.setFrameRate`, `preferredFrameRate`, `requestedFrameRate` or `FrameRateCategory`
  usage. Nothing in the app pins, caps or requests a display cadence.
- **No scoped refresh hints added**: the spec allows them "only if trace-justified"; the traces collected below do not
  show the OS policy starving frames, so none were added, and the app never holds a high-refresh request while
  idle/background.

## Macrobenchmark module and emulator run (sequential Sonnet 5.5 pass, 26 Oct 2026)

`:benchmark` (`com.android.test`, `androidx.benchmark:benchmark-macro-junit4:1.5.0`) targets the release-like
`benchmark` build type of `:app` (debug-signed, profileable, never shipped; cleartext limited to the host emulators).

**Signed-in journeys are real now.** `tools/benchmark-seed.mjs` creates one demo account on the **local** Auth emulator
and writes 2 accounts and 54 transactions to the **local** Firestore emulator (it refuses any non-loopback host and uses
the emulator's own admin token for Firestore). The benchmark types the demo credentials into the **real sign-in screen**
once; Firebase Auth keeps the session across the benchmark's process restarts. There is no production auth bypass and no
live call. Selectors were verified against the real UI (`By.desc("Add an entry")`, `By.text("RECEIVED THIS MONTH")`,
nav labels picked by lowest on-screen position because Home's tiles reuse the same words).

Setup: **emulator Pixel_6a (sdk_gphone16k_arm64, API 37), host GPU (Apple M4 / Metal), 60 Hz emulated display**,
5 iterations per journey, `CompilationMode.Full()` for frame journeys, `None()` for cold start. Raw data:
`motion/benchmark/pixel6a-emulator-benchmarkData.json`; table: `motion/benchmark/summary.md`
(`tools/summarize-benchmark.py`); 40 Perfetto traces kept outside git in
`openspec/evidence/pixel6a-feedback/perfetto/`. Frame budget at 60 Hz is 16.7 ms.

| Journey | Frames/iter (med) | Frame CPU p50 / p95 / p99 ms | Overrun p50 / p95 / p99 ms | Late frames |
| --- | --- | --- | --- | --- |
| Home hero scroll (parallax) | 164 | 2.9 / 5.2 / 16.7 | -10.4 / -7.4 / 3.0 | 1.3% |
| Nav retargeting (capsule) | 232 | 3.0 / 7.3 / 18.0 | -10.5 / 1.6 / 4.7 | 5.8% |
| Nav retargeting, animations off | 52 | 4.2 / 9.7 / 13.5 | -9.8 / -4.0 / -1.1 | 0.4% |
| Routes + list scroll (Activity, Plans, More) | 279 | 2.5 / 4.8 / 7.3 | -11.4 / -8.4 / 4.3 | 1.7% |
| Add sheet + tab switch + time dial drag | 304 | 2.7 / 8.6 / 21.1 | -10.9 / -3.5 / 15.1 | 3.7% |
| Add sheet + dial, animations off | 266 | 2.7 / 7.6 / 22.9 | -11.0 / -4.3 / 16.6 | 3.4% |

Cold start TTID (None): sign-in screen median 236 ms (197–245); signed-in Home median 181 ms (174–298).

Reading it honestly: most frames finish ~10 ms early; the late tail is the first frames of a sheet opening and the
capsule's rapid retargeting (p99 overrun ≤ 17 ms, i.e. at most about one dropped 60 Hz frame). An emulator host
GPU is not a Pixel 6a GPU, so this is evidence the journeys run and the distribution is healthy, **not** a physical
60 Hz or 120 Hz result. The earlier software-rendered run (swiftshader, p50 frame CPU 40-58 ms) is kept as
`pixel6a-emulator-swiftshader-superseded-benchmarkData.json`: those numbers were the renderer, not the app, and are not used.
No refresh-rate hint was added: nothing in the traces shows the OS policy starving frames, and none is held while idle.

## Screen recordings (emulator, scripted `adb input`, 540x1200)

`motion/recordings/`: `hero-parallax.mp4` (Home fling both ways), `nav-capsule-retarget.mp4` (tile into Activity then
eight rapid tab taps), `add-sheet-and-time-dial.mp4` (sheet, Expense/Income/Transfer, time dial drag),
`nav-capsule-reduced-motion.mp4` (the same taps with Android animations off). Predictive-Back gesture was not recorded
(`adb input` cannot perform the edge gesture).

## What was verified (real, emulator only)

- Motion/unit suites pass in `:app:testDebugUnitTest` (see the handoff for the full count), including
  `NavSessionTest`, `NavCapsuleControllerTest`, `MotionPolicyTest`, `NavRouteMotionTest`, `SelectionHapticsTest`,
  `ClockDialInputTest` and `HeroParallaxTest` (the pure, bounded parallax maths).
- Instrumented on **emulator-5554 Pixel_6a (API 37)**: `ClockDialGestureTest` (drag/tap/keyboard semantics) and
  `HomeScreenUiTest` pass. `CategoryGridEvidenceTest` captures hero/sheet states.
- Fresh-launch vs restore for the navbar session is owned by `NavSession` (a `rememberSaveable` Saver living in the
  signed-in graph): a fresh launch starts AddOnly on Home; config change / process restore keeps the session; sign-out
  removes the graph and starts a new one. Covered by `NavSessionTest`.

## Not measured (pending, not passed)

- **Physical Pixel 6a 60 Hz feel and physical 120 Hz**: no physical device is available. **Unverified.**
- **Physical haptics** (clock detent softness/latency): the fake haptic sink only proves the changed-detent ticks,
  duplicate suppression and the 40 ms fast-scrub cap. **Unverified.**
- Predictive-Back recording (needs a real edge gesture).

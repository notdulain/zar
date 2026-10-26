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
`SelectionHapticsTest`, `HeroParallaxTest` pass in `:app:testDebugUnitTest` (1200 tests, 0 failures).

## High-refresh audit (source)

- **No fixed 60 Hz cap**: a search of `app/src/main/java` finds no `Choreographer`, `withFrameNanos`,
  `Surface.setFrameRate`, `Window.setFrameRate`, `preferredFrameRate`, `requestedFrameRate` or `FrameRateCategory`
  usage. Nothing in the app pins, caps or requests a display cadence.
- **No scoped refresh hints added**: the spec allows them "only if trace-justified"; the traces collected below do not
  show the OS policy starving frames, so none were added, and the app never holds a high-refresh request while
  idle/background.

## Macrobenchmark module and emulator run (26 Oct 2026)

A `:benchmark` module now exists (`benchmark/build.gradle.kts`, `com.android.test`, `androidx.benchmark:benchmark-macro-junit4:1.5.0`)
targeting a new release-like, non-debuggable, profileable `benchmark` build type in `:app` (debug-signed; never shipped).
Journeys: `coldStartup` (StartupTimingMetric, 5 iterations), `firstScreenScrollFrameTiming` (FrameTimingMetric, 5
iterations), and signed-in `homeHeroScrollFrameTiming` / `navRetargetFrameTiming` (FrameTimingMetric) which `assumeTrue`
a seeded signed-in session and are **skipped** without the local Firebase emulators + a seeded user.

Run: `./gradlew :benchmark:connectedBenchmarkAndroidTest` on **emulator Pixel_6a (sdk_gphone16k_arm64, API 37)**,
`CompilationMode.None()` (run-from-apk). Raw JSON: `benchmark/pixel6a-emulator-benchmarkData.json`; 10 Perfetto traces
were written to `benchmark/build/outputs/connected_android_test_additional_output/...` (build output, not committed).

- `coldStartup` TTID: median 3377.6 ms, max 4352.2 ms over 5 runs (min 3136.2 ms). This is an **emulator, interpreted
  (no AOT) cold start** — not a device number and not a smoothness result.
- `firstScreenScrollFrameTiming`: only 5–6 frames per iteration, because the first screen without a seeded account is
  the sign-in screen, which is not scrollable; `frameDurationCpuMs`/`frameOverrunMs` were therefore not produced. This
  journey needs the signed-in Home to be meaningful and is **pending** a seeded session.

Emulator timings are labelled as such and are **not** a physical 60/120 Hz result. No refresh-rate hint was added.

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

- **Physical 120 Hz**: no capable physical device is available. Emulators cannot prove 120 Hz; **unverified**.
- **Physical haptics**: clock detent softness/latency is a physical check; the fake haptic sink only proves the
  changed-detent ticks, duplicate suppression and the 40 ms fast-scrub cap. **Physical haptics unverified**.
- **Signed-in frame timing / p95 frameOverrunMs / missed deadlines**: the signed-in journeys are skipped without a
  seeded Firebase-emulator session, and the emulator produced no `frameOverrunMs` for the non-scrollable first screen.
  The Macrobenchmark module and Perfetto capture path now exist and run; the signed-in numbers remain **pending**.
- Screen recordings of scroll/parallax/capsule/predictive Back: not captured in this session.

# Motion and high-refresh evidence

Branch `t3/resume-pixel6a-implementation` (consolidated recovery worker). Author: OpenCode DeepSeek V4.1 Flash
(authorized fallback). This file records exactly what was and was not measured; no physical or high-refresh claim is made.

## High-refresh audit (source)

- **No fixed 60 Hz cap**: a search of `app/src/main/java` finds no `Choreographer`, `withFrameNanos`,
  `Surface.setFrameRate`, `Window.setFrameRate`, `preferredFrameRate`, `requestedFrameRate` or `FrameRateCategory`
  usage. Nothing in the app pins, caps or requests a display cadence.
- **No scoped refresh hints added**: the spec allows them "only if trace-justified"; with no trace collected here,
  none were added, so the app never holds a high-refresh request while idle/background.
- No Macrobenchmark or Baseline Profile module exists in the project (`settings.gradle.kts` has one `:app` module;
  no `macrobenchmark` dependency), so the five-iteration FrameTimingMetric/Perfetto journeys were **not run**.

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
- **Frame timing / p95 frameOverrunMs / missed deadlines / Perfetto traces**: not collected (no profileable benchmark
  module and no physical device). No FPS number is reported.
- Screen recordings of scroll/parallax/capsule/predictive Back: not captured in this session.

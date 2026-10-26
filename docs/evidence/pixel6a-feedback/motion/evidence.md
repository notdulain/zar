# Motion and high-refresh evidence

Branch `t3/resume-pixel6a-implementation` (consolidated recovery worker). Author: OpenCode DeepSeek V4.1 Flash
(authorized fallback). This file records exactly what was and was not measured; no physical or high-refresh claim is made.

## Sequential motion pass: 120 Hz readiness, parallax, bounce, springs (Sonnet 5.5, 26 Oct 2026)

Branch `t3/0f0d2620`, measured build SHA `481b82c` (benchmark build, `-Pzar.useEmulators=true`). Everything below was run on
**emulator Pixel_6a (sdk_gphone16k_arm64, Android 17 / API 37), host GPU, 60 Hz only**. No physical device was attached.

### Display capability and what the app asks of it

`tools/refresh-capabilities.sh` (output kept as `benchmark/device-capabilities-481b82c.json`): supported modes `[60.0]` Hz,
active render rate 60.0, no adaptive-refresh-rate support, battery saver off, thermal status 0, animator scale 1. Verdict
printed by the tool: *no physical 120 Hz mode here: any 120 Hz result stays pending*. The Pixel 6a's official maximum is 60 Hz.

- **Frame-time based, no cap**: the only frame loop the app owns is the capsule spring (`NavCapsuleController.moveTo`); it
  evaluates a spring at the frame's own time and ends when settled. A search of `app/src/main` finds no `Choreographer`,
  `setFrameRate`, `preferredFrameRate` or infinite animation. `NavCapsuleControllerTest` asserts no frame callback is left
  outstanding after a settle or a cancel, so an idle or backgrounded app asks for no cadence.
- **Cadence independence** (`FrameCadenceTest`, a frame clock delivering 60 / 90 / 120 Hz frame times): every token settles
  in the same wall-clock time at each rate and sits at the same position at 50-300 ms (settle 333 ms, navSelection 300 ms,
  sheetSettle 289-300 ms, route 300 ms; frames delivered 21 / 31 / 41 for settle). This proves the tokens are
  cadence-independent. It does **not** prove a physical 120 Hz panel presents 120 frames.
- **No refresh hint added, on purpose.** Compose UI 1.12.1 already votes a `FrameRateCategory.High` hint whenever a layer
  moves or resizes on API 35+ (`NodeCoordinator`, `GraphicsLayerOwnerLayer`: the vote is made per frame and cleared after
  each draw, so nothing is held at idle) and the platform boosts on touch. The capsule is drawn in the draw phase after a tap,
  where neither vote applies, so on a 120 Hz API 35+ phone it could settle at a lower rate than the touch boost. No trace here
  can show that. If the 120 Hz run below shows the capsule frames at a lower cadence than the page travel, the candidate
  is `Modifier.preferredFrameRate(FrameRateCategory.High)` on the capsule while `controller.isMoving`, nothing else.
- **Repeatable 120 Hz harness (not run, no device):** `tools/refresh-capabilities.sh <serial>`; build with
  `-Pzar.useEmulators=false` equivalents is not needed, the benchmark seeds the local emulators, so for a physical phone use the
  host's emulator addresses over `adb reverse`, run `:benchmark:connectedBenchmarkAndroidTest` with `ANDROID_SERIAL`, then
  `tools/trace-frame-summary.py <traces>`: it prints the display period and the cadence frames were actually produced at, so a
  device that downclocks to 60/90 Hz is reported as that cadence.

### Five-iteration results (one unmeasured warm-up pass per journey, same fixture and `CompilationMode.Full()`)

| Journey | Iteration | Frames | Overrun p95 ms | Late frames | Late % |
| --- | --- | --- | --- | --- | --- |
| routesAndListScrollFrameTiming | 1 | 282 | -8.9 | 4 | 1.4% |
| routesAndListScrollFrameTiming | 2 | 285 | -8.3 | 9 | 3.2% |
| routesAndListScrollFrameTiming | 3 | 284 | -8.9 | 6 | 2.1% |
| routesAndListScrollFrameTiming | 4 | 281 | 1.3 | 15 | 5.3% |
| routesAndListScrollFrameTiming | 5 | 283 | -9.0 | 4 | 1.4% |
| **routesAndListScrollFrameTiming** | all 5 | 1415 | | 38 | **2.7%** |
| navRetargetFrameTiming | 1 | 235 | -5.8 | 8 | 3.4% |
| navRetargetFrameTiming | 2 | 239 | -7.4 | 6 | 2.5% |
| navRetargetFrameTiming | 3 | 249 | -7.6 | 5 | 2.0% |
| navRetargetFrameTiming | 4 | 254 | -7.8 | 6 | 2.4% |
| navRetargetFrameTiming | 5 | 238 | -6.7 | 7 | 2.9% |
| **navRetargetFrameTiming** | all 5 | 1215 | | 32 | **2.6%** |
| addSheetAndDialReducedMotionFrameTiming | 1 | 264 | -6.4 | 6 | 2.3% |
| addSheetAndDialReducedMotionFrameTiming | 2 | 271 | -0.1 | 13 | 4.8% |
| addSheetAndDialReducedMotionFrameTiming | 3 | 268 | -6.7 | 6 | 2.2% |
| addSheetAndDialReducedMotionFrameTiming | 4 | 266 | -6.4 | 6 | 2.3% |
| addSheetAndDialReducedMotionFrameTiming | 5 | 269 | -6.2 | 9 | 3.3% |
| **addSheetAndDialReducedMotionFrameTiming** | all 5 | 1338 | | 40 | **3.0%** |
| homeHeroScrollFrameTiming | 1 | 165 | -8.7 | 0 | 0.0% |
| homeHeroScrollFrameTiming | 2 | 161 | -8.4 | 0 | 0.0% |
| homeHeroScrollFrameTiming | 3 | 164 | -5.5 | 0 | 0.0% |
| homeHeroScrollFrameTiming | 4 | 164 | -6.2 | 0 | 0.0% |
| homeHeroScrollFrameTiming | 5 | 165 | -6.4 | 0 | 0.0% |
| **homeHeroScrollFrameTiming** | all 5 | 819 | | 0 | **0.0%** |
| addSheetAndDialFrameTiming | 1 | 298 | -6.9 | 3 | 1.0% |
| addSheetAndDialFrameTiming | 2 | 317 | -7.0 | 4 | 1.3% |
| addSheetAndDialFrameTiming | 3 | 303 | -7.2 | 3 | 1.0% |
| addSheetAndDialFrameTiming | 4 | 300 | -7.4 | 5 | 1.7% |
| addSheetAndDialFrameTiming | 5 | 297 | -7.2 | 3 | 1.0% |
| **addSheetAndDialFrameTiming** | all 5 | 1515 | | 18 | **1.2%** |
| navRetargetReducedMotionFrameTiming | 1 | 51 | -5.5 | 0 | 0.0% |
| navRetargetReducedMotionFrameTiming | 2 | 52 | -6.5 | 0 | 0.0% |
| navRetargetReducedMotionFrameTiming | 3 | 51 | -4.6 | 0 | 0.0% |
| navRetargetReducedMotionFrameTiming | 4 | 51 | -4.4 | 1 | 2.0% |
| navRetargetReducedMotionFrameTiming | 5 | 51 | -6.2 | 0 | 0.0% |
| **navRetargetReducedMotionFrameTiming** | all 5 | 256 | | 1 | **0.4%** |

Targets (verification.md): p95 overrun <= 0 ms, <= 5% late, no app-attributable stall >= 100 ms.

- Aggregates: all frame journeys are inside the targets in this run (nav retarget 2.6% late, p95 overrun -7.0 ms; hero scroll 0.0%).
- **Two individual iterations miss and are reported, not averaged away:** routes/list-scroll iteration 4 (p95 overrun +1.3 ms, 5.3%
  late) and the animations-off dial iteration 2 (4.8%, p95 -0.1 ms, under the limit but 13 late frames).
- **Run-to-run variance is large on this emulator.** The same nav-retarget journey on the same code measured 5.3% late / p95
  +1.4 ms and 15.4% late / p95 +4.3 ms on the same afternoon while the host was busy (a Canva renderer at 200% CPU and a stuck
  Gradle daemon at 100% were running; the stuck daemon was stopped). The baseline recorded before this pass (808c5fa) was 5.8% /
  +1.6 ms. The quiet-host figures above are the ones that count, and the earlier ones are why a physical-device run is still required.

Attribution from the Perfetto FrameTimeline (`tools/trace-frame-summary.py`, 40 traces kept outside git in
`openspec/evidence/pixel6a-feedback/perfetto-481b82c/`, ignored): of 7,371 app frames only **6 (0.08%) were "App Deadline Missed"**
(3 in routes/list scroll, 3 in the animations-off dial journey); 133 (1.8%) were presentation misses on the system side
("Dropped Frame", display HAL, prediction error). No frame in the nav-retarget or hero-scroll journeys was missed by the app.

| Journey | app frames | app-missed | system-side late | UI frames > period | worst UI frame ms |
|---|---|---|---|---|---|
| Add sheet + dial | 1788 | 0 | 12 | 10 | 35.0 |
| Add sheet + dial, animations off | 1352 | 3 | 18 | 11 | 87.6 |
| Home hero scroll | 825 | 0 | 19 | 0 | 8.5 |
| Nav retargeting | 1429 | 0 | 46 | 0 | 9.9 |
| Nav retargeting, animations off | 256 | 0 | 0 | 0 | 10.5 |
| Routes + list scroll | 1721 | 3 | 38 | 0 | 15.9 |

The 87.6 ms UI frame (no >= 100 ms stall anywhere) is an input event whose 79.6 ms is 61.4 ms inside the binder call
`IDisplayEventConnection::requestNextVsync`: it is waiting on SurfaceFlinger, not running app code. The recurring app misses
that do exist are first compositions of a screen on tab arrival (about 12-40 ms of `Recomposer:recompose`/`applyChanges`) and
the first frames of the Add sheet; both are rare after warm-up. They were not reduced in this pass.

**Parallax does not recompose or rebuild charts per pixel (measured):** in the Home hero-scroll journey each iteration runs
about 199 UI frames of which 15 recompose (16 before this pass), a constant independent of scroll distance; layers move through
`graphicsLayer` reads in the draw phase.

### Parallax verification (`HeroParallaxUiTest`, real owners, controlled clock)

Every top card was re-checked against its scroll owner (list in the table above). Lazy heroes are read by item key
(`ZarHeroItemKey`) from where the item rests; content padding above a hero does not displace it. Verified: zero at rest;
fractional then capped travel (text 6% capped at 8 dp); offscreen terminal value and identical position on return;
reduced motion leaves layers at their layout position; a click inside a displaced layer lands where it is drawn; a
`ScrollState` owner behaves the same. Backgrounds: the Home gradient's uncovered strip is the same `#05070B` as the gradient's
first stop and always lies above the viewport (a layer moves by at most 20% of the scroll distance), and Activity paints its
first-stop colours above the hero. No scroll physics are replaced: parallax reads the position and never writes it.

### Capsule bounce, interruption and legibility

`NavCapsuleController` springs a measured rectangle (x and width). Real mid-flight frames of Home -> More, captured by
`ZarBottomNavMotionTest.midFlightFramesAreCapturedForTheEvidenceRecord`, are in `screens/nav-capsule-flight-home-to-more.png`
(each label is dark on the pill and light on the capsule at every step). Measured travel on the emulator (left edge, px,
16 ms frames): `16.0 56.8 151.5 268.5 387.0 494.8 585.8 658.1 712.3 750.6 775.9 791.1 798.9 801.6 801.0 798.7 ... 780.0`
settling in about 27 frames with a 21.6 px (8.2 dp) natural overshoot that the controller caps at 6 dp by sliding the capsule
(never squashing it). A retarget at frame 6 continued `+91 +45 -9 -40 -54 -57` px/frame (momentum carries, no stall; before the
fix the first frame after a tap did not move). A short hop overshoots at most 4 px. `ZarMotion.NavMaxOvershoot` is now used.

### Springs and press

`SettleMotionTest`: a section growing 40 -> 200 dp settles monotonically with no overshoot (about 27 frames), a reversal
mid-settle retargets, reduced motion is immediate; a pressed button shrinks to 98% and returns, dragging away cancels with no
click, reduced motion never scales it.

### Navigation (4.2, 4.3)

`RouteMotionNavTest` (production policy in a real NavHost): push child enters from 411 dp (the logical end) and rests at 0
with no overshoot while the parent recedes to exactly 20% of the width; the sheet-style income route rises from 914 dp while its
parent never moves; reduced motion has no travel; predictive Back through the activity dispatcher: at 40% progress the child had
moved 38 dp, cancel restored it at 0 with two entries still on the stack, and a committed back popped exactly one.
`NavRevealMotionTest`: the pill rises 8 dp over about 9 frames with the capsule on the real tab from its first frame.

### Test totals

`:app:testDebugUnitTest` 1,220 tests, 0 failures; `assembleDebug`, `compileDebugAndroidTestKotlin`, `:benchmark:assembleBenchmark` pass.
Full `connectedDebugAndroidTest` on the emulator (excluding `SignInFlowTest`, which crashes the shared instrumentation process
with `Cannot call useEmulator() after instance has already been initialized`): 330 run, 2 skipped, 3 failed, none in motion code:
`CaptureRepositoryTest` (`FirebaseApp was deleted`), `CatchUpReminderWorkerTest` (notification lookup) and `MoreScreenTest.showsConnectedRowsOnly`
(still asserts there is no "Export data" row; the CSV export PR added one). These were not touched.

### Still pending

Physical 120 Hz (any), physical 60 Hz feel on a Pixel 6a, physical haptics, a recorded edge-swipe predictive Back, Figma dark
prototypes (HTTP 401) and the opus prototype links for tasks 1.2 and 6.4. None is claimed here.

## Earlier pass (kept for reference)

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

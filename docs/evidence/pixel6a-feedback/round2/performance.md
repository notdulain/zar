# Pixel 6a round 2: pager / navigation frame timing (sanitized summary)

This file has two parts. **Part 1** re-measures the final code (`f5e82df`, which includes `1e1443b` and `12a6242`) on the physical phone
on 28 Oct 2026. **Part 2** is the earlier 27 Oct measurement of the build that became `1084274`; it is kept for the baseline and is
**not** the final code.

## Part 1: final code on the phone (28 Oct 2026)

Tested source: `f5e82dffebb817c5bb73e52fe548f8bd3eeebf9b` (app code identical to `12a6242`). One explicit adb serial (Pixel 6a,
60 Hz panel: supported rates 60/30/20 Hz, every trace reports a 16.60 ms period and 59.6 to 61.1 Hz active cadence, so
**no 90/120 Hz claim**). Isolated `com.example.zar.perf` installs only; the original `com.example.zar` was not launched, stopped,
reinstalled or written (its `lastUpdateTime` is unchanged).

Build flags (both): `-Pzar.appIdSuffix=.perf -Pzar.useEmulators=true -Pzar.emulatorHost=127.0.0.1`, `BuildConfig.USE_FIREBASE_EMULATORS = true`,
`EMULATOR_HOST = 127.0.0.1`, synthetic benchmark user from `tools/benchmark-seed.mjs` over `adb reverse tcp:9099/8080` (removed afterwards).
- **benchmark** build: `DEBUG = false`, minified, no Baseline Profile (JIT). Judge release-like feel from this.
- **debug** build: `DEBUG = true`, JIT, no R8. This is what the owner's phone runs, so it is shown too.

Phone state, unchanged by the run: system theme Dark (`cmd uimode night yes`; switched to Light for the Light traces and restored), density
override 356, font scale 1.0, animator/transition/window scale 1.0, no refresh-rate overrides, plugged in (battery 37%, 37.7 C at start,
40.1 C at the end). Debug Light/Dark was set through the `.perf` app's own appearance preference; benchmark Light used the system theme.
Tools: `tools/pager-profile.py` (Perfetto + `dumpsys gfxinfo`, `--settle 6` = settled, `--settle 1.5` = started before the idle warm-up
finished), `tools/trace-frame-summary.py`. Raw traces stay outside git. **app-missed** = FrameTimeline "App Deadline Missed";
**system-side** = other late presentation types (replaced/dropped before vsync, no app deadline overrun); **worst UI** = longest
`Choreographer#doFrame` on the main thread. Two runs per settled scenario, three per early cold scenario, one for reduced motion.

### Benchmark build (release-like)

| Scenario | Theme | Frames | App-missed | Worst UI frame |
| --- | --- | --- | --- | --- |
| Warm swipes | Dark | 241 / 254 | **0 / 0** | 9.2 / 10.8 ms |
| Warm swipes | Light | 248 / 252 | **0 / 0** | 8.1 / 9.0 ms |
| First swipe after cold start (settled) | Dark | 40 / 39 | 3 / 2 | 443 / 33.6 ms |
| First swipe after cold start (settled) | Light | 36 / 41 | 2 / 1 | 37.3 / 31.1 ms |
| First swipe, before the idle warm-up ended | Dark | 39 / 38 / 37 | 1 / 2 / 2 | 26.8 / 31.1 / 31.1 ms |
| First swipe, before the idle warm-up ended | Light | 41 / 37 / 38 | 2 / 2 / 2 | 31.1 / 32.1 / 30.9 ms |
| Home collapse scroll | Dark | 335 / 337 | 1 / 2 | 15.2 / 12.3 ms |
| Home collapse scroll | Light | 339 / 337 | 1 / 1 | 15.3 / 10.5 ms |
| 8 + 4 tab taps (stationary nav, capsule retarget) | Dark | 357 / 339 | 0 / 1 | 11.3 / 9.5 ms |
| 8 + 4 tab taps | Light | 348 / 362 | 0 / 0 | 10.5 / 11.1 ms |
| Settings push (child route over retained tabs) | Dark / Light | 54 / 49, 52 / 52 | 0 / 1, 1 / 1 | 33.9 / 28.0, 30.3 / 34.9 ms |
| Settings Back (rightward pop) | Dark / Light | 22 / 22, 23 / 22 | 0 / 0, 0 / 1 | 9.8 / 10.6, 11.7 / 16.5 ms |

`gfxinfo` agrees for warm swipes: 0, 1, 0, 0 janky frames of 242, 255, 249, 253 (p90 10 to 11 ms, p99 13 to 14 ms). Tap traces list 29 to 54
system-side late frames per run (frames replaced before vsync, not app overruns), as before.

### Debug build (the owner's build type)

| Scenario | Theme | Frames | App-missed | Worst UI frame |
| --- | --- | --- | --- | --- |
| Warm swipes | Dark | 240 / 236 | 5 / 5 | 13.7 / 14.2 ms |
| Warm swipes | Light | 246 / 247 | 5 / 4 | 17.7 / 14.3 ms |
| First swipe after cold start (settled) | Dark | 30 / 35 | 5 / 5 | 83 / 64 ms |
| First swipe after cold start (settled) | Light | 36 / 37 | 8 / 6 | 68 / 68 ms |
| First swipe, before the idle warm-up ended | Dark | 32 / 38 / 41 | 7 / 6 / 5 | 66 / 71 / 63 ms |
| First swipe, before the idle warm-up ended | Light | 34 / 41 / 37 | 6 / 6 / 6 | 64 / 65 / 63 ms |
| Home collapse scroll | Dark / Light | 339 / 340, 335 / 333 | 6 / 7, 7 / 7 | 20.4 / 17.8, 24.1 / 17.8 ms |
| Tab taps | Dark / Light | 438 / 445, 459 / 431 | 2 / 2, 3 / 2 | 24.4 / 24.0, 23.8 / 32.7 ms |
| Settings push | Dark / Light | 58 / 61, 50 / 56 | 1 / 1, 1 / 1 | 72.5 / 62.2, 71.8 / 73.0 ms |
| Settings Back | Dark / Light | 22 / 23, 22 / 22 | 2 / 3, 2 / 2 | 24.5 / 47.8, 37.2 / 32.7 ms |
| Reduced motion (Dark, one run): warm swipes / taps / push / pop | Dark | 140 / 334 / 27 / 9 | 6 / 2 / 1 / 4 | 17.3 / 26.1 / 65.5 / 53.4 ms |

Debug warm swipes: 4 to 5 app-missed frames in about 240 (about 2%; baseline 6 in 210 with a 145 ms worst frame), and **no UI-thread frame
longer than 17.7 ms**. Debug cold first swipes are 63 to 83 ms of recomposition and layout in unoptimised code, as on 27 Oct.

### What the latest measurement says

- **No regression from `1e1443b` (tab Back slides right) and `12a6242` (tabs stay composed under child routes).** Warm swipes, the
  capsule, Home collapse, tab taps and the Settings Back are all at or better than the 27 Oct numbers, in both themes. Warm benchmark
  swipes are 0 app-missed in 995 frames across the four runs.
- **The Back routes are not janky.** The rightward Settings pop is 0 or 1 app-missed in about 22 frames with a worst frame of 10 to 17 ms
  (benchmark). The recordings of tab Back, Settings edge cancel then commit, and the Add-transaction sheet edge cancel then commit are in
  `phone/` (Light and Dark, 540x1200 re-encodes of the phone's screen recording; synthetic data only).
- **Cold first swipe is the one remaining cost, and it is JIT-cold code.** The benchmark build still drops 1 to 2 frames at the first swipe
  after a process start (one doFrame of about 31 ms: roughly 15 ms recompose + 10 ms layout + 4 ms draw of the incoming page). Check: full
  AOT compilation of the same benchmark install (`cmd package compile -m speed -f`, then reset) brought the worst frame to 17 to 19 ms and
  the count to 1 per run (3 runs). That bounds what a Baseline Profile could win, but it only helps release builds, not the debug build
  the owner runs, and costs a profile-generation setup, so none was added.
- **One 443 ms outlier is the GPU, not the app.** In 1 of 10 benchmark cold runs (and the 152 ms frame on 27 Oct, 1 of 4) the UI thread
  waited in `postAndWait` for the RenderThread, which spent 200 ms in one Vulkan `Clear` plus 240 ms in `queueBuffer` inside `DrawFrames`
  (first use of the full-screen buffer after launch). No app code is on that path.
- **Settings push** drops one frame in the benchmark build (about 12 ms recompose + 12 ms layout of a screen composed for the first time).
  That is first composition, not retained-page work, so no change was made.
- Not claimed: how any of this *feels*, haptic quality, or anything at 120 Hz. A feel judgement needs the owner on the phone.

## Part 2: 27 Oct 2026, the build that became `1084274` (earlier, not the final code)

Source: Perfetto traces and `dumpsys gfxinfo` captured on the **physical Pixel 6a** (isolated `com.example.zar.perf` install beside the
real app, synthetic benchmark data from the local Firebase emulators, one explicit adb serial, original app untouched) on 27 Oct 2026.
Raw traces stay outside git (they carry process and device metadata); only the counts below are committed. Numbers come from
`tools/trace-frame-summary.py` (FrameTimeline) and `gfxinfo`.

**Display: 60 Hz.** Every trace reports a 16.60 ms frame period and an active cadence of 59.6 to 60.2 Hz. The Pixel 6a panel is 60 Hz,
so no 90/120 Hz claim exists or is made here. "Late" is judged against 16.6 ms.

## What the counts mean

- **app-missed** = FrameTimeline "App Deadline Missed": the app's frame overran its deadline. This is counted whoever caused the
  stall, so a frame stuck waiting on the GPU still lands here (see the 152 ms frame below).
- **worst UI frame** = longest `Choreographer#doFrame` on the app's main thread.
- Two builds: **debug** (JIT, no R8: slow first-run code) and **benchmark** (minified, closer to release). Judge release feel from
  *benchmark*; debug is shown because the user's phone runs debug builds.
- Two stages: **baseline** = the 93f7566 build (no neighbour-tab warm-up); **final** = the build that became 1084274 (idle warm-up of
  the neighbouring tabs). Two iterations each for the final runs.

## Tab swipes

| Scenario | Build | Stage | Frames | App-missed | Worst UI frame |
| --- | --- | --- | --- | --- | --- |
| Warm swipes | debug | baseline | 210 | 6 | 145 ms |
| Warm swipes | debug | final | 372 / 241 | 6 / 3 | 23 ms / 14 ms |
| Warm swipes | benchmark | baseline | 232 | 6 (+2 system-side) | 90 ms |
| Warm swipes | benchmark | final | 245 / 241 | **0 / 0** | 10 ms / 10 ms |
| First swipe after a cold start | debug | baseline | 16 | 9 | 153 ms |
| First swipe after a cold start | debug | final | 34 / 37 | 6 / 5 | 63 ms / 74 ms |
| First swipe after a cold start | benchmark | baseline | 31 | 3 | 126 ms |
| First swipe after a cold start | benchmark | final | 30 / 37 | 3 / 2 | 152 ms / 32 ms |
| First swipe, started before the idle warm-up finished | benchmark | final | 39 / 45 | 1 / 2 | 30 ms / 33 ms |
| First swipe, started before the idle warm-up finished | debug | final | 35 / 37 | 5 / 5 | 69 ms / 75 ms |

`gfxinfo` agrees: benchmark warm swipes 0 janky of 245 and 0 of 242 (p90 10 ms, p99 14 ms); debug warm swipes 5 of 267 and 6 of 242;
cold first swipe janky 4 of 31 and 2 of 38 (benchmark), 6 of 35 and 4 of 38 (debug).

Other motion on the same builds:

| Scenario | Build | Frames | App-missed | Notes |
| --- | --- | --- | --- | --- |
| Home scroll (collapse), run 1 | benchmark final | 333 | 1 | worst UI frame 17 ms (baseline 338 frames, 2 app-missed) |
| Home scroll, run 1 | debug final | 336 | 6 | worst 38 ms (baseline 332 frames, 8 app-missed, worst 52 ms) |
| 12 rapid tab taps | benchmark final | 382 | 0 | worst 11 ms |
| 12 rapid tab taps | debug final | 439 | 2 | worst 26 ms |
| Settings push / pop (debug, later build) | debug | 44 / 23 | 3 / 3 | gfxinfo janky 3 of 44 and 3 of 23; pop p90 32 ms |

The tap traces also list 35 (benchmark) and 129 (debug) frames with present type "Dropped Frame" and **no jank type**: a frame the app
submitted that was replaced by a newer one before the next vsync. They are not late frames, and the tool reports them as "system-side".

## The cold first swipe, honestly

- **The warm-swipe gain is real**: benchmark 0 app-missed frames across 486 frames (baseline 6), debug worst frame 145 ms to 14 to 23 ms.
- **The cold-first-swipe spike is reduced, not gone.** Benchmark: 3 and 2 app-missed frames in the two settled runs, 1 and 2 when started early (baseline 3).
- The 152 ms benchmark frame is **not app code**. Its UI thread was waiting 150 ms in `postAndWait` for the RenderThread, which spent
  138 ms in one Vulkan `Clear` inside `DrawFrames`. That is GPU/driver first-use work. It happened in one of four benchmark cold runs; the
  other three worst frames were 30 to 33 ms (about 15 ms recomposition plus 10 ms layout of the incoming page).
- Debug cold swipes cost 63 to 75 ms in `Recomposer:recompose` plus `measureAndLayout` of the incoming page, with JIT-compiled code. That is
  a debug-build property; the benchmark build does the same work in about 30 ms.
- No further app change was made: nothing in the main-thread traces points at avoidable app work beyond what the idle warm-up already
  moved out of the swipe. The remaining safe lever is a **Baseline Profile** for release builds (precompiled startup and pager code);
  it needs a profile run on a device and is not part of this change.

## Limits

- Part 2 predates `1e1443b` and `12a6242`; Part 1 is the latest-code measurement (28 Oct 2026).
- 120 Hz: not a feature of this phone. 60 Hz only.
- Emulator frame counts (emulated GPU) are not used here as performance evidence.
- Sample sizes are small (1 to 3 runs per scenario). Treat a single frame either way as noise; the stable facts are the zero-missed warm
  benchmark swipes and the one JIT-cold frame at the first swipe.

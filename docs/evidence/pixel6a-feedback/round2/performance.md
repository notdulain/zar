# Pixel 6a round 2: pager / navigation frame timing (sanitized summary)

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

## Not verified (limits)

- **Not re-measured on the phone after the last code changes.** The numbers above are from the build that became `1084274`. `1e1443b`
  (Back from a tab slides right) and `12a6242` (tabs stay composed under child routes) changed `TabPager`/`ZarNavHost` afterwards; the
  phone was not attached to repeat the swipe traces. Re-run `tools/run-motion-benchmark.sh <serial>` when it is back.
- 120 Hz: not a feature of this phone. 60 Hz only.
- Emulator frame counts (emulated GPU) are not used here as performance evidence.

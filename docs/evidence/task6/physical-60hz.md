# Physical device motion record — Pixel 6a, 60Hz

Run on 2026-11-04 against source `3ecf1cf` (Floating Nav row restored) on a physical Pixel 6a, Android 16. The benchmark installed a side-by-side `com.example.zar.perf` build over USB with `adb reverse` to local emulators and synthetic data. The phone's primary `com.example.zar` app and its data were not launched, updated or cleared (its `lastUpdateTime` is unchanged), and the `.perf` test packages were removed afterwards. No raw traces or phone screenshots are committed.

## What the device reports

- Supported display modes: **60Hz only** (`supportedRefreshRates` 60/30/20). No adaptive refresh, no 90Hz or 120Hz mode.
- Active render rate 60.0Hz. Battery saver off, animator scale 1, thermal status 1.
- Verdict from `tools/refresh-capabilities.sh`: *no physical 120 Hz mode here: any 120 Hz result stays pending*.

**120Hz is not testable on this hardware and stays pending.** Nothing here is a 120Hz result.

## Results (8 of 8 journeys passed, 5 iterations each)

- routesAndListScrollFrameTiming | frames/iter min 364, med 368, max 371 | frame CPU ms p50 6.3 / p90 8.8 / p95 9.5 / p99 11.9 (n=1838) | overrun ms p50 -8.3 / p90 -4.3 / p95 -3.6 / p99 7.6 (n=1838, 30 late (1.6%))
- coldStartup | TTID ms min 409, med 418, max 425
- coldStartupSignedInHome | TTID ms min 415, med 419, max 431
- navRetargetFrameTiming | frames/iter min 328, med 348, max 354 | frame CPU ms p50 6.4 / p90 9.6 / p95 10.7 / p99 12.4 (n=1726) | overrun ms p50 -7.9 / p90 -4.0 / p95 -1.5 / p99 11.3 (n=1726, 82 late (4.8%))
- addSheetAndDialReducedMotionFrameTiming | frames/iter min 1, med 1, max 3 | frame CPU ms p50 13.8 / p90 19.8 / p95 19.8 / p99 19.8 (n=9) | overrun ms p50 -1.2 / p90 5.6 / p95 5.6 / p99 5.6 (n=9, 2 late (22.2%))
- homeHeroScrollFrameTiming | frames/iter min 164, med 164, max 165 | frame CPU ms p50 4.7 / p90 6.4 / p95 7.4 / p99 11.9 (n=822) | overrun ms p50 -10.5 / p90 -8.2 / p95 -6.4 / p99 -2.2 (n=822, 5 late (0.6%))
- addSheetAndDialFrameTiming | frames/iter min 36, med 37, max 37 | frame CPU ms p50 4.8 / p90 5.9 / p95 6.5 / p99 10.7 (n=184) | overrun ms p50 -10.1 / p90 -8.7 / p95 -8.0 / p99 -3.3 (n=184, 1 late (0.5%))
- navRetargetReducedMotionFrameTiming | frames/iter min 29, med 31, max 33 | frame CPU ms p50 5.1 / p90 11.1 / p95 12.7 / p99 15.4 (n=155) | overrun ms p50 -9.6 / p90 -3.6 / p95 -1.6 / p99 1.7 (n=155, 2 late (1.3%))

Trace cadence (`tools/trace-frame-summary.py --package com.example.zar.perf`): navigation, list scroll, hero scroll and sheet/dial journeys ran at 59.6–60.5Hz. All traces together: 5805 frames, app-missed 56 (0.96%), system-side late 124 (2.14%). Reduced-motion journeys have few frames by design, since they resolve instantly. Cold-start traces show lower or `nan` cadence because little is drawn while launching.

## Not established

- Human motion and haptic feel on this phone (frame timings do not replace a person judging the Back slide and nav capsule).
- 120Hz, and any 90Hz behaviour.
- TalkBack, and exhaustive route/picker gesture cancel and commit.
- Launcher masks across other launchers and upgrade data preservation.

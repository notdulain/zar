# Integrated acceptance — refine Zar brand, Plans, Home and auth

This record separates tests, actual app navigation captures, component fixtures and remaining human/device acceptance. See `build.json` for the production source SHA, APK hash, flags and emulator geometry. All new screenshots use synthetic localhost fixtures. The physical phone was not changed during task 6.

## Integration fixes

- Moved New plan from the header to bottom contextual chrome. Four nav slots keep identical bounds across context changes; measured chrome clearance keeps the last action scrollable above it. The app retains main's merged Add speed dial and swipe-to-dismiss feature.
- Tooltip now uses the shared reduced/disabled motion policy. Updated Home tests to follow the month popup and removed disclaimer.
- Retained legacy and multiple goals through same-ID retry and offline persisted-client restart, with main preference and sign-out isolation assertions in `GoalCatalogReplayTest`.
- Restored the Phosphor license notice during full-branch review.
- Merged main `5ec0582`, resolving shared chrome, sheet and picker conflicts while preserving physical-right Back and immediate reduced-motion Back.
- Actual cold-launch checks caught an indefinitely repeating native splash drawable. Bounded the native sweep in `573a497`; a force-stop launch then reached Home in 4,818ms on this emulator. The Compose pending-session bar remains indeterminate.

## Automated validation

- Final merged unit suite: **1,368 tests, zero failures/errors/skips**.
- Local Firestore rules: **47 passed, zero failed**, via `node --test rules.test.mjs` against existing localhost emulator. The normal emulator wrapper encountered occupied ports, so the direct runner reused the already running local service. No live deployment occurred.
- `testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin :benchmark:assemble -Pzar.useEmulators=true`: passed after integration. Release assemble also passed after integration.
- Broad emulator suite before main integration: **58 passed of 59**; remaining capture-journey failure was a test selector, fixed in the final runtime rerun. Included Home (15), Plans (6), New plan (4), Spending (6), registration (4), auth motion (4), real SDK auth flows (4), route transitions (9), sheets (4), real SDK goal replay (1), fixed chrome (1), runtime capture journey (1).
- After main integration: **15 affected tests passed** (6 speed dial, 8 sheet host, 1 contextual chrome); the capture helper was corrected to inspect unmerged goal semantics and distinguish retained offscreen pager labels. Final runtime journey: **1 passed**, recording all 12 Light/Dark runtime screenshots. The final Home/Plans/registration evidence batch passed **26/26**, and the runtime journey was repeated successfully after the last loading-status contrast fix.

Passing subsets are not presented as a single all-green end-to-end invocation. Earlier failed diagnostic attempts were test selection/fixture setup failures; their screenshots were replaced after correction.

Fresh font-size captures caught clipping in shortcut cards and spending split rows at 200%; `e1565eb` grows cards and stacks label/value pairs at enlarged sizes. `78aa06c` keeps white system glyphs on a dark status band during overall Home loading/errors.

## Reproduction

1. Start local Auth/Firestore emulators; install `firestore-tests` dependencies with npm ci.
2. Run `node tools/task6-seed.mjs`. It refuses live services through the benchmark fixture, seeds synthetic accounts/entries/goals and uploads current rules to the localhost app project only.
3. Build debug + AndroidTest APKs with `-Pzar.useEmulators=true`.
4. Install explicitly on a selected emulator, then invoke `am instrument` with the desired classes. Do not use Gradle connected tests against the user's primary phone package.
5. Run `tools/task6-display-capture.sh emulator-5556` for display variants; the script refuses physical serials and restores changed settings.

## Scope and acceptance limits

The review covered new reporting calculations, refund/void/FX reconciliation, stable goal IDs and reservations, main preference rules, navigation/window wrappers, fonts/assets, and shared chrome integration. Automated tests support these findings; this is not an independent human code review.

Figma handoff: [revised contract](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=768-22790), with explicit owner overrides for month placement, removed eye/disclaimer and Material splash bar. Source audit: `docs/design/refine-zar/handoff.md`. Representative app captures have been visually inspected; the design page's state studies do not establish complete runtime parity.

**Still pending:** 120Hz motion (this phone has no 120Hz mode; the 60Hz physical run is in `physical-60hz.md`), human motion/haptic judgment, TalkBack and exhaustive route/picker gesture cancel/commit coverage, all launcher masks across supported launchers, independent PR approval, and recovery verification for local-only data from the task5 cleanup incident. Emulator frames and policy tests do not count as physical refresh-rate proof. PR stays draft for those acceptance items. Production rules deployment and merge require separate authorization/review and were not performed.

## Remaining sign-off checklist

| Task | State |
|---|---|
| 6.1 automated unit, SDK/rules and build checks | Complete for available local tooling |
| 6.2 runtime/component/display evidence | Available emulator checks captured; exhaustive physical/TalkBack/mask checks pending |
| 6.3 physical 60/120Hz and human feel | 60Hz measured on a physical Pixel 6a ([`physical-60hz.md`](physical-60hz.md)); 120Hz unavailable on this hardware; human feel pending |
| 6.4 commits, pinned evidence and review | Draft PR prepared; independent approval pending |

The task5 data-loss incident is retained in the PR's review notes. This turn did not reinstall or clear the user's primary phone app, change its settings, merge code or deploy production rules.

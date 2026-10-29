# D08 / SCRUM-21: integration and data-security verification

Verified on 29 October 2026 with local code and Firebase emulators (`demo-zar`). One queued-capture ownership race was reproduced and fixed: switching users cannot redirect a captured draft into the next account. No deployed service, Jira state or release package was changed.

Base: `origin/main` at `163df2d34ca5c40de693105119d1d8419b90749b`. Branch: `feature/SCRUM-21-integration-security`.

## Acceptance evidence

| Jira acceptance check (verbatim) | Evidence and bounds |
|---|---|
| Domain calculations can be tested without Firebase; screens/ViewModels do not directly access databases. | [Architecture audit](01-architecture-boundaries.md), DomainLayerPurityTest and LayerBoundaryTest. Presentation checks reject storage SDKs and concrete data implementations. Scanner limits and one existing data-to-presentation formatter allowlist are documented. |
| Test offline edits/reconnection, replay duplication, corrections and undo across feature boundaries. | [Nine persisted cases](02-offline-ledger-integration.md): manual, quick-note, capture, transfers/fees and evidence links. Server records and balance/spending projections agree; originals remain immutable. [UI journeys](05-financial-journeys.md) also correct/void offline and verify the server after reconnect. |
| Test two different users, sign-out and queued capture to prove no cross-user display or upload. | [Two-user capture tests and fixed race](03-capture-user-isolation.md), plus UserScopedDataTest. Real emulator identities, encrypted Room staging, production processing, visible repository flows and both users' server collections. SMS broadcast/WorkManager execution is outside these cases. |
| Test Firebase ownership/type rules and the complete manual and quick-note journeys; record defects and evidence. | [44 rules cases](04-firestore-security.md), [three persisted UI journeys](05-financial-journeys.md), [14 screenshots](screenshots/). Production screens/ViewModels/use cases/storage are exercised; a small host replaces NavHost/Hilt assembly and external interpretation. Four SignInFlowTest cases exercise real MainActivity/auth/navigation separately. Expense save/edit/undo and quick-note actual/expected confirmation are verified; a single continuous full-app AI route and every financial form permutation are not claimed. |

## Final combined results

Counts below are from the final run, not totals of repeated development runs. [test-results.json](test-results.json) lists every Android case and aggregate JVM/rules results.

| Check | Passed | Failed / skipped |
|---|---:|---:|
| `./gradlew testDebugUnitTest assembleDebug` | 1,264 JVM tests; debug APK built | 0 / 0 |
| Combined Android instrumentation | 41 tests, 346.40 seconds | 0 / 0 |
| Full `firestore-tests/rules.test.mjs` | 44 tests, 3.40 seconds | 0 / 0 |
| Strict OpenSpec validation | Valid | — |

Android class counts: offline ledger 9; capture user isolation 2; financial UI journeys 3; rejected ledger writes 4; atomic transfers 2; capture repository 10; automatic capture 6; user-scoped data 1; sign-in flow 4.

Device: Pixel_6a AVD, Android 17 / API 37. Screenshots: light theme, 1080 × 2400, synthetic data. The final combined run refreshed every image after allowing device-clock IME/inset animations to settle. No UI asset or Figma frame was changed.

## Reproduce

Use Android SDK Platform 37, a booted emulator, JDK 17 for Gradle, JDK 21+ for Firestore, Node and Firebase CLI. Set the SDK path in ignored `local.properties`. JVM checks require no Firebase initialization or emulator:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Runtime tests need ignored `app/google-services.json` containing fake `demo-zar` configuration. Preserve any live configuration by using a fresh worktree. A minimal fake file can be generated only when none exists:

```bash
python3 - <<'PY'
import json
from pathlib import Path
p = Path('app/google-services.json')
assert not p.exists(), 'Preserve existing config; use a fresh test worktree'
p.write_text(json.dumps({
  'project_info': {'project_number': '123456789', 'project_id': 'demo-zar'},
  'client': [{'client_info': {'mobilesdk_app_id': '1:123456789:android:0123456789abcdef',
    'android_client_info': {'package_name': 'com.example.zar'}},
    'api_key': [{'current_key': 'AIzaSyDemoOnlyNotARealFirebaseApiKey12345'}]}],
  'configuration_version': '1'
}))
PY
# Separate terminal, JDK 21+, from the checkout whose rules are under test:
firebase emulators:start --only auth,firestore --project demo-zar
# Build and install the emulator-enabled APKs:
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pzar.useEmulators=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class \
com.example.zar.integration.OfflineLedgerIntegrationTest,com.example.zar.integration.CaptureUserIsolationTest,com.example.zar.integration.FinancialJourneyTest,com.example.zar.core.data.LedgerWriteOutcomeTest,com.example.zar.core.data.TransferBatchTest,com.example.zar.features.capture.CaptureRepositoryTest,com.example.zar.features.capture.AutoCaptureTest,com.example.zar.features.auth.UserScopedDataTest,com.example.zar.features.auth.SignInFlowTest \
com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
# Require OK (41 tests), not just an adb shell exit code.
```

See [the journey report](05-financial-journeys.md) for screenshot extraction. A later normal build changes the output APK configuration, not the installed emulator build.

Run rules checks after Android cases: their setup clears demo documents. With the emulator already running:

```bash
cd firestore-tests
npm ci
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node --test rules.test.mjs
```

Alternatively `npm test` starts Firestore through emulators:exec; stop the existing instance first to avoid port conflicts. This machine reused its existing Firebase 11.10.0 / rules-unit-testing 4.0.1 installation after an incomplete install hit disk exhaustion. No dependencies or lockfiles changed. The final rules suite loaded this checkout's rules.

## Defects and verification limits

- **Fixed:** queued capture ownership race, with deterministic pre-fix reproduction, owner-bound write and regression results in [03](03-capture-user-isolation.md).
- **Architecture debt:** reminders data imports one presentation formatter. [01](01-architecture-boundaries.md) documents the exact allowlist; it does not permit presentation storage access.
- **Test fixture corrections:** JUnit setup return type and screenshot animation timing. Final images show stable persisted detail and full controls.
- **Not run:** entire Android/UI suite; real SMS broadcast/WorkManager scheduling; live Firebase/App Check/model quality; physical devices; dark theme, large fonts or TalkBack; Android lint; release packaging. Build-variant wiring, backend functions and Worker code were unchanged, so their release/backend checks were not needed.
- Existing process-death tooling was not rerun; D08's final cases cover offline/reconnect in one process.
- Expense and quick-note production layers run together inside a test host; auth/navigation runs separately in MainActivity. A single uninterrupted full-app AI journey remains a further end-to-end check. No pixel-parity or release-approval claim is made.

## Task commits

| OpenSpec task | Commit | Deliverable |
|---|---|---|
| 1.1 | `f53f375` | Architecture guard and audit |
| 2.1 | `9fde1d4` | Nine offline cross-feature cases |
| 3.1 | `501ba5e` | Capture owner-binding fix and two-user cases |
| 4.1 | `1600660` | 44-case rules suite and security report |
| 5.1 | `5e75d23` | Three production-layer UI journeys and runtime evidence |
| 6.1 | Final evidence commit containing this report | Combined results, refreshed screenshots and acceptance matrix |

Tasks 1–3 were implemented by Sonnet 5.5 and recovered as ordered commits after provider failures. At the user's explicit request, Codex completed tasks 4–6 directly. Ignored OpenSpec artifacts remain at `openspec/changes/scrum-21-integration-security/`.

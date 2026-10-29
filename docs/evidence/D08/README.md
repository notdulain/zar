# D08 / SCRUM-21: integration and data-security verification

Evidence for Jira SCRUM-21 ("Test Clean Architecture, integration and data security"). Everything here ran against local code, the Firebase Emulator Suite (`demo-zar`) and Android emulators. Nothing used the live Firebase project, live credentials or a paid AI provider. Nothing here is physical-device, App Check, production-model or release-readiness evidence.

Each section is written by the task that produced it. The final acceptance matrix is added by OpenSpec task 6.1.

| Task | Report | Status |
|---|---|---|
| 1.1 Architecture boundaries | [01-architecture-boundaries.md](01-architecture-boundaries.md) | Done |
| 2.1 Offline cross-feature ledger consistency | [02-offline-ledger-integration.md](02-offline-ledger-integration.md) | Done |

## Reproduce the environment

```bash
export JAVA_HOME=/path/to/jdk-17        # JDK 17 works; any JDK 17+ is supported by the build
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # ignored by git
./gradlew :app:testDebugUnitTest         # no emulator, no google-services.json needed
```

## Reproduce the emulator runs

Needs JDK 21 for the Firestore emulator, an Android emulator (these runs: `Pixel_6a` AVD), and a **git-ignored** `app/google-services.json` pointing at the fake project `demo-zar` with a dummy API key. The app's `Application.onCreate` initialises the default Firebase app, so an instrumented run cannot start without some such file. Never use the real file for these runs and never commit it.

```bash
firebase emulators:start --only auth,firestore --project demo-zar      # from the repo root so firestore.rules is loaded
./gradlew :app:connectedDebugAndroidTest -Pzar.useEmulators=true \
  -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>[,<another>]
```

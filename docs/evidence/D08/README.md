# D08 / SCRUM-21: integration and data-security verification

Evidence for Jira SCRUM-21 ("Test Clean Architecture, integration and data security"). Everything here ran against local code, the Firebase Emulator Suite (`demo-zar`) and Android emulators. Nothing used the live Firebase project, live credentials or a paid AI provider. Nothing here is physical-device, App Check, production-model or release-readiness evidence.

Each section is written by the task that produced it. The final acceptance matrix is added by OpenSpec task 6.1.

| Task | Report | Status |
|---|---|---|
| 1.1 Architecture boundaries | [01-architecture-boundaries.md](01-architecture-boundaries.md) | Done |

## Reproduce the environment

```bash
export JAVA_HOME=/path/to/jdk-17        # JDK 17 works; any JDK 17+ is supported by the build
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # ignored by git
./gradlew :app:testDebugUnitTest         # no emulator, no google-services.json needed
```

# 1.1 Architecture boundaries

Acceptance check: *Domain calculations can be tested without Firebase; screens/ViewModels do not directly access databases.*

## What was added

`app/src/test/java/com/example/zar/architecture/`

| File | Role |
|---|---|
| `LayerBoundaryRules.kt` | Source-text scanner. Reads imports and fully qualified names in code, skips comments and KDoc, reports `path:line [rule] source`. |
| `LayerBoundaryTest.kt` | Applies the rules to the real tree and proves the scanner with synthetic sources. 13 tests. |
| `DomainLayerPurityTest.kt` | Unchanged. Still the original "no `android.`/`androidx.`/`firebase`/`dagger` import in a domain package" check. |

### Rules on the real source tree

| Layer | Files scanned | Forbidden |
|---|---|---|
| Presentation (`**/presentation/**`, `core/navigation/**`, `MainActivity.kt`) | 100+ | Firebase/Firestore SDK, Room, `androidx.sqlite`, `android.database.sqlite`, `android.database.Cursor`, SQLCipher, any `com.example.zar.**.data.*` concrete class, `com.example.zar.di.*` |
| Domain (`**/domain/**`) | 100+ | `android.*`, `androidx.*` (including Compose and Room), Firebase, Dagger/Hilt, `**.data.*`, `**.presentation.*`, `di.*`. `javax.inject.Inject` remains allowed |
| Data (`**/data/**`) | 30+ | `**.presentation.*` (one named exception, below) |
| Domain tests under `src/test/**/domain/**` | 40+ | Firebase, `android.*` imports, `androidx.test`, Robolectric |

Also asserted: no `@HiltViewModel` / `: ViewModel()` class lives outside a presentation package, and every scan checks it found enough files so a moved directory cannot make it pass vacuously.

Deliberately allowed, and covered by tests so it stays allowed: domain repository interfaces and use cases imported by ViewModels, `androidx.hilt.navigation.compose.hiltViewModel`, Compose, lifecycle and `android.database.ContentObserver` (the system animation-setting observer in `core/presentation/motion/ProvideZarMotion.kt`, not storage).

### Scanner self-tests

Synthetic sources prove the guard fails when it should and reports the location, for example:

```
features/demo/presentation/DemoViewModel.kt:4 [firebase-sdk] import com.google.firebase.firestore.FirebaseFirestore
```

Cases: Firebase import in a ViewModel, Room/SQLite/Cursor/SQLCipher in a screen, concrete data class or Hilt module in presentation, a fully qualified `com.google.firebase...` call inside code, framework / data / presentation imports in domain, Firebase/Robolectric/Android in domain tests; and negative cases for comments, KDoc and legitimate imports.

### Mutation check (manual, not committed)

Temporarily adding `import com.google.firebase.firestore.FirebaseFirestore` to `features/home/presentation/viewmodel/HomeViewModel.kt` made `presentationNeverImportsStorageSdksOrConcreteDataCode` fail with:

```
features/home/presentation/viewmodel/HomeViewModel.kt:3 [firebase-sdk] import com.google.firebase.firestore.FirebaseFirestore
```

The file was restored; the diff contains no production change.

## Audit findings

| Finding | Result |
|---|---|
| Presentation importing Firebase, Room, SQLite or a concrete data class | None. |
| Domain importing a framework, data or presentation package | None. |
| ViewModels outside presentation | None. |
| `features/reminders/data/PaymentReminders.kt` imports `core.presentation.format.formatWithCode` | **Upward import, left as is.** A pure money formatter used to build a notification string. It does not touch storage, so it is outside the "screens/ViewModels do not access databases" check, and moving the formatter is a refactor outside this ticket. It is recorded as a named allowlist entry in `LayerBoundaryTest`; the test fails if the entry becomes stale. |
| `ZarApplication.kt` imports `core.data.remote.AppCheckInstaller` | Application bootstrap, not a screen or ViewModel. Not scanned as presentation. |
| `di/RepositoryModule.kt`, `di/FirebaseModule.kt` import Firebase/data | Correct: Hilt wiring is where interfaces meet implementations. |
| `CaptureViewModelTest.kt` sits under `capture/domain/` and imports `androidx.lifecycle.viewModelScope` | JVM-only, no Firebase or Android runtime. Allowed. Filing a ViewModel test under `domain/` is untidy but harmless. |

No production code changed in this task.

## Commands and results

```bash
./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest \
  --tests 'com.example.zar.architecture.*' --tests '*.domain.*'
```

| Group | Tests | Failed | Skipped |
|---|---|---|---|
| `architecture` (`DomainLayerPurityTest` 1 + `LayerBoundaryTest` 13) | 14 | 0 | 0 |
| `core.domain` (calculators, projector, money, use cases) | 43 | 0 | 0 |
| `features.*.domain` | 459 | 0 | 0 |
| **Total** | **516** | **0** | **0** |

Run on JDK 17 (Temurin 17.0.18), no emulator, no `google-services.json`, no network, which is what "domain calculations run without Firebase" means in practice: the domain tests run as plain JVM unit tests and none of their sources import Firebase, Android, Robolectric or AndroidX Test (checked by `domainTestsRunWithoutFirebaseOrAndroidRuntime`).

## Limits

- The guard is a text scan, not a type-resolving analysis. It catches imports and fully qualified names; it cannot see a data object reached only through an inferred type or reflection. Hilt modules are the only place concrete classes are named, and presentation importing `di` is rejected.
- It enforces direction (presentation → domain ← data) and the storage-SDK ban. It does not judge whether a use case is well designed.
- Instrumented and UI tests are not scanned (they are allowed to wire real repositories).

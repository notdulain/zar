# Zar

Personal finance tracking for Sri Lankan users.

## Current state

The app foundation is in place: Jetpack Compose with Material 3, MVVM, Hilt, Firebase (Auth, Firestore) and type-safe Navigation Compose. Minimum SDK is 26. Feature implementations are added through the team backlog.

`features/settings` is a small reference slice showing the full path: `SettingsScreen` → `SettingsViewModel` → `ObserveAppPreferencesUseCase` → `AppPreferencesRepository` (domain interface) → `ReportingCurrencyPreferencesRepository` (data), bound in `di/RepositoryModule`. Copy this shape for new features.

## Architecture

Use one app module, organised by feature:

```text
app/src/main/java/com/example/zar/
├── MainActivity.kt
├── core/
│   ├── domain/
│   │   ├── model/
│   │   ├── repository/
│   │   └── usecase/
│   ├── data/
│   │   ├── local/
│   │   └── remote/
│   ├── presentation/
│   │   ├── components/
│   │   └── theme/
│   └── navigation/
├── di/
└── features/
    └── <feature>/
        ├── presentation/
        ├── domain/
        └── data/
```

| Folder | Put this code here |
|---|---|
| `presentation` | Compose screens, ViewModels and screen state |
| `domain` | Models, use cases, calculations and repository interfaces |
| `data` | Repository implementations, database/API access and data mapping |
| `core` | Code genuinely shared by multiple features |
| `di` | Hilt modules that connect interfaces and implementations |

Screens call ViewModels, which call domain use cases. Use cases depend on repository interfaces; the data layer implements them. Domain code must not import Android, Firebase, Compose or Room. Presentation must not access databases or external APIs directly.

Add smaller packages such as `model`, `usecase`, `repository`, `screen` and `viewmodel` within a feature when its code needs them. Empty directories have `.gitkeep` files so Git preserves the scaffold; remove a placeholder when adding real code.

## Features and ownership

| Features | Main owner |
|---|---|
| `auth`, `accounts`, `settings`; shared foundation and AI service | Dulain |
| `freelance`, `crypto`, `bills`, `capture`; received-income work in `transactions` | Luchitha |
| `transactions`, `quicknote`; capture review UI | Sanuk |
| `home`, `plans`, `allowance`; masking and exports in `settings` | Sumuditha |

## Run locally

Requirements: a recent Android Studio, JDK 17 or newer, and **Android SDK Platform 37** (SDK Manager → SDK Platforms). Compose libraries require compileSdk 37; the app still targets SDK 36 and runs on Android 8.0 (API 26) and up.

1. Clone the repository and open its root folder in Android Studio.
2. Let Android Studio configure the local Android SDK and sync Gradle.
3. Get the team's `google-services.json` privately and place it in `app/` (optional for building — see below).
4. Run the `app` configuration on an emulator or device.

Use the committed Gradle wrapper:

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests, including the domain-purity check
```

### Testing against the Firebase emulators

Sign-in and data tests run against the local Firebase Emulator Suite, never the live project. With an Android emulator running:

```bash
npx -y firebase-tools@latest emulators:exec --only auth,firestore "./gradlew connectedDebugAndroidTest -Pzar.useEmulators=true"
```

`-Pzar.useEmulators=true` makes a debug build use the emulators (`BuildConfig.USE_FIREBASE_EMULATORS`). Normal builds always use the live project.

Without `app/google-services.json` the build still succeeds and prints a warning, but Firebase is not initialised, so any screen that injects `FirebaseAuth` or `FirebaseFirestore` will crash at runtime. Never commit that file.

Machine paths, IDE files, build output, Firebase configuration and credentials are ignored. Privileged AI/service keys belong on the backend.

## Shared domain contracts

Models, repository interfaces, saving rules and total calculations shared by every feature are described in [docs/domain-contracts.md](docs/domain-contracts.md). Read it before saving or totalling money.

AI quick notes use OpenRouter through a Cloudflare Worker. Deployment, Firebase authentication/App Check, secrets, Android configuration and evaluation are in [docs/openrouter-quick-notes.md](docs/openrouter-quick-notes.md). Worker checks run with `npm ci && npm run check && npm test` in `worker/`. The original [Gemini feasibility results](docs/ai-quick-notes-feasibility.md) and [Firebase callable](docs/ai-quick-notes-service.md) remain as historical references.

## Layer rules

- `DomainLayerPurityTest` fails if any file under a `domain` package imports `android.*`, `androidx.*`, `com.google.firebase.*` or `dagger.*`. Use `javax.inject.Inject` in domain classes.
- Bind each new repository interface to its implementation in `di/RepositoryModule`.
- Inject Firebase objects (from `di/FirebaseModule`) only into `data` classes.
- Add new destinations to `core/navigation/ZarRoutes.kt` and `ZarNavHost.kt`.
- Firestore rules live in `firestore.rules`; run `npm install && npm test` in `firestore-tests/` (needs Java) after changing them, and deploy with `npx -y firebase-tools@latest deploy --only firestore:rules`.

## Jira feature command

Run a Jira task through OpenSpec planning, Sonnet 5.5 implementation in a separate worktree, Figma verification, one commit per completed task, and a PR using the repository template:

- **Claude Code:** `/jira-feature SCRUM-42`
- **Codex:** `$jira-feature SCRUM-42` (or select `jira-feature` with `/skills`).

Both accept a Jira URL, an optional Figma selection URL and `base=<branch>`. The default base is the latest `origin/main`.

The shared workflow is in [.agents/skills/jira-feature/SKILL.md](.agents/skills/jira-feature/SKILL.md). Jira, Figma for UI tasks, OpenSpec and GitHub access must be available. In T3, it discovers the exact Sonnet 5.5 model from the provider catalog. Native Claude Code can use the included Sonnet worker; Codex needs a Claude delegation provider such as T3. If that provider is unavailable, the command reports the blocker instead of switching models. Restart the agent session if the new command is not listed.

OpenSpec plans remain git-ignored. UI PRs include screenshots captured from the running app. Invocation includes feature commits, pushing and PR creation; merging and deployment are separate actions.

## Team backlog

[Jira backlog](https://03dulain.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog) · [Approved wireframes](https://www.figma.com/design/p5SITMwzIDaa76eHLI3zB0/Places-App?node-id=91-2)

Keep received money separate from expected payments. Transfers are not income or spending. Feature freeze: 22 October 2026. Submission: 30 October 2026.

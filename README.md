# Zar

Personal finance tracking for Sri Lankan users.

## Current state

This repository contains the Android starter and the Clean Architecture folder scaffold. The existing entry screen still uses XML and AppCompat. Compose, Hilt, Firebase and feature implementations will be added through the team backlog.

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

1. Clone the repository and open its root folder in Android Studio.
2. Let Android Studio configure the local Android SDK and sync Gradle.
3. Run the `app` configuration on an emulator or device.

Use the committed Gradle wrapper. A debug build can be requested with `./gradlew assembleDebug`.

Machine paths, IDE files, build output, Firebase configuration and credentials are ignored. When Firebase is configured, obtain the team's `google-services.json` separately and place it in `app/`. Privileged AI/service keys belong on the backend.

## Team backlog

[Jira backlog](https://03dulain.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog) · [Approved wireframes](https://www.figma.com/design/p5SITMwzIDaa76eHLI3zB0/Places-App?node-id=91-2)

Keep received money separate from expected payments. Transfers are not income or spending. Feature freeze: 22 October 2026. Submission: 30 October 2026.

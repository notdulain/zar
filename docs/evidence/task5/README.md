# Brand, startup and authentication

Source: Zar UI, file `bBBJuQz1Cg8N34FcisKBs9`, inspected 2026-11-03.

- Z38 `266:3338`, mark `281:3520`: unchanged exported SVG in `app/src/main/assets/brand/zar-mark.svg`; Android vectors and launcher renditions derive from it. Gold dot #E2AE4F, white mark, #05070B launcher field. `scripts/brand/generate-launcher.py` reproduces outputs with cairosvg/Pillow.
- Z43/Z44 `269:3642` / `269:3669`; Dark `470:12987`; keyboard `560:546`. Both modes keep dusk; Light uses white CTA, Dark accent CTA. Existing shared Geist styles supersede Mono labels.

Device acceptance and validation are recorded below as performed. Figma exports are references, not runtime evidence. No personal phone screenshots are committed.

## Implementation and checks

- Launcher assets: `e8eff77`; session/Retry: `58278f0`; registration: `028c215`; shared motion: see following task commit.
- `:app:testDebugUnitTest`: 1,365 tests, zero failures/errors/skips. Includes two session restoration/retry regressions.
- `:app:assembleDebug`, `:app:assembleRelease`, `:app:compileDebugAndroidTestKotlin`: passed. Final auth label-clipping fix also passed debug and instrumentation APK assembly.
- Physical Pixel 6a, Android 16: 13 direct instrumentation tests passed (`RegisterScreenUiTest`, `SignInScreenUiTest`, `AuthMotionUiTest`). These render production composables with synthetic state; they do not submit credentials or write ledger data. Final Light registration, Dark inline errors and IME captures inspected locally. The OS keyboard remains the user's keyboard.
- Motion uses existing shared critically damped settle/press tokens; there is no new Opus motion handoff. Route entrance is saved per route instance; editing and theme changes do not replay it. Reduced/Instant policy tests verify immediate input. Existing navigation Back policy is retained.
- Local session restoration does not require network. An actual flow failure produces error/Retry. Session observation is eager in its ViewModel and has no artificial delay, so dropping UI subscribers does not restart loading.

## Device-test incident

The initial Gradle `connectedDebugAndroidTest` run uninstalled the primary app during cleanup and removed local app data. The updated app was reinstalled and the user informed that sign-in is required again; server data was not modified by these UI fixtures. Recovery of local-only unsynced data is unverified. This fails the planned upgrade/data-preservation acceptance. Subsequent runs used explicit `adb install -r` followed by `am instrument`, without automatic uninstall cleanup. Do not run Gradle connected tests against the user's primary package; use a separate test package/device.

## Acceptance still pending

Full launcher/app-info/themed-mask checks across launchers, authenticated data recovery after the incident, cold/warm launch recordings across saved appearances, actual system animator-scale-zero/large-font/landscape runs and human motion review remain pending. Unit/UI policy checks are not a claim of those device checks or of complete task-5 acceptance. No physical-phone screenshots or raw private traces are committed.

## Splash loading-bar follow-up

The session indicator is a 120×3dp Material indeterminate bar, white over a muted track with rounded ends and no segment gap. Android 12+ uses the same exported Zar mark with a clipped animated-vector sweep in its native launch icon. The OS controls native splash lifetime and system animation scaling; Compose additionally honors Zar's Reduce motion setting with a static track. No keep-on-screen condition or minimum loading timer was added. Updated debug build installed with `adb install -r`; Pixel 6a cold launch succeeded (1,171ms) without AndroidRuntime errors.

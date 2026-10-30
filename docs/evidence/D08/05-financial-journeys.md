# 5.1 Manual and quick-note journeys

`FinancialJourneyTest` runs production Compose bodies and their actual ViewModels, domain use cases and Firestore repositories. Each case gets a fresh anonymous emulator user and two synthetic accounts (Test Cash, Test Bank), each with LKR 10,000 opening funds. Device: Pixel_6a AVD, Android 17 / API 37, 1080 × 2400, light theme. Firebase project: `demo-zar` only.

## Verified behavior

| Case | UI actions and persistence assertions |
|---|---|
| Manual save → offline correction → undo → reconnect | Type LKR 500, choose Cash/Groceries, enter a note, open/cancel the time picker and save. Read the saved server ledger and show its production detail screen. Disable Firestore networking, change amount to 650 through the correction screen, verify its offline banner and corrected detail. Open the offline undo sheet and confirm. Reconnect: original stays 500, current corrected value stays 650, exactly one correction and one void exist, and spending is zero. |
| Quick-note input → interpretation → review/edit → confirmations | Type a note, interpret through `GenerateQuickNoteDraftUseCase`, verify there are no transaction/expected documents before confirmation and that missing account blocks saving. Open the real proposal editor, pick Cash and change 1250 to 1300. Confirm actual expense, verify the expectation is still absent; then confirm the expectation separately. Server has exactly one transaction and one expected payment, no persisted draft/raw note; repeated confirmation is inert, note is cleared, spending is 1300 and the persisted detail renders. |
| Offline note → manual fallback → reconnect | Disable Firestore networking and set the injected connectivity source offline. Type a note, verify Interpret is disabled and the offline explanation visible. Fill the manual form, verify the note survives, choose account/category and save LKR 200 locally. Reconnect and verify the single server transaction. |

The connectivity source and external interpretation repository are deterministic test boundaries. All financial repositories, calculations, validation, ViewModels and screen callbacks are production code. The interpreter checks the exact note and supplied two-account context before returning one actual proposal with a missing account and one expectation. There are no live model calls.

## Commands and results

```bash
./gradlew :app:connectedDebugAndroidTest -Pzar.useEmulators=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.zar.integration.FinancialJourneyTest
```

**3 passed, 0 failed, 0 skipped**, 100.95 seconds in the Gradle XML report. A first attempt found a test-only JUnit setup return-type error, corrected before this successful run. No additional production defect was demonstrated by these journeys.

The direct runner also passed **3/3** (89.14 seconds) and retained all fourteen PNGs. Capture now allows device-clock IME/inset animations to settle, and the final combined run refreshes those images after that capture-only adjustment.

Gradle's device test runner removes its installed package after completion, including the screenshots in its cache. To retain screenshot files, install the built emulator APKs and run the same test class directly:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.zar.integration.FinancialJourneyTest \
  com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
# Require OK (3 tests) in the output; am instrument's shell exit alone is insufficient.
adb exec-out run-as com.example.zar tar -cf - -C cache/d08-evidence . > /tmp/d08-evidence.tar
mkdir -p docs/evidence/D08/screenshots
tar -xf /tmp/d08-evidence.tar -C docs/evidence/D08/screenshots
```

## Screenshot index and Figma comparison

All images in [screenshots/](screenshots/) are actual runtime captures with synthetic data, never Figma exports.

| Files | States | Reference |
|---|---|---|
| 01–03 | Manual time picker, completed form, saved detail | Z06 `125:1674` |
| 04–07 | Offline correction, corrected history, offline undo sheet, voided detail | Z54 `296:3776`, Z56 `296:4213` |
| 08–12 | Quick-note writing, actual/expected review, editor/account choice, actual saved, persisted detail | Z125 `403:6783`, Z127 `403:7354`, Z128 `403:7604` |
| 13–14 | Offline quick note and manual fallback | Z130 `403:8104`, Z34 `251:3624` |

Compared the writing/review hierarchy, missing-account blocking, actual-vs-expected distinction, picker controls and offline/undo wording with those Figma references. Existing Zar typography, surfaces and action controls are used unchanged. These are functional evidence and a scoped visual inspection, not a pixel-parity claim: the small host omits the underlying Home screen behind sheets, and the manual body is shown without the app's full Add-session container. Dynamic amounts, dates and system bars differ from Figma samples. No Figma frames or UI assets were changed.

## Boundaries not covered

- The test host replaces NavHost/Hilt assembly and seeds authentication/accounts. It selects the persisted entry's detail after saving; it does not prove the full app's post-save navigation. Actual screen edits and actions call their production ViewModels and storage path.
- This is the expense path, not every income, transfer, refund or expected-payment editing permutation. Task 2 covers cross-feature transfer/refund-related accounting boundaries separately.
- No production AI accuracy, HTTP/App Check, physical device, dark theme, TalkBack or large-font result is claimed. Existing fixed-state UI tests are not counted as backend journeys.

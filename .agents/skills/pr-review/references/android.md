# Zar Android verification recipe

Use the repository's current Gradle configuration and relevant tests; class names and counts change between PRs. These commands are working recipes, not a requirement to rerun every suite for documentation or low-impact changes.

## Isolated worktree

Fetch the real PR branch/base first. Use a fresh task-specific temporary path and local branch. If needed for a debug build, copy ignored `local.properties` and `app/google-services.json` from the user's configured checkout into the worktree. Keep them ignored and never print their contents. Use local Firebase emulators for backend integration; do not seed or mutate the live project as a review shortcut.

## Build and tests

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Use a log file for long output and poll with short waits so the user gets progress updates. Run one Gradle command at a time in a worktree. Count actual unit results from `app/build/test-results/testDebugUnitTest/TEST-*.xml`; inspect failures and the lint report rather than reporting success from APK existence. Run Firestore rule tests if rules or their contract change, using the project's emulator/test configuration.

## Emulator

Resolve the SDK from `local.properties`, `ANDROID_SDK_ROOT`, or the local installation (on this Mac: `$HOME/Library/Android/sdk`). Inspect `adb devices` and available AVDs first. Reuse a suitable existing device without taking ownership of it. For a new review emulator, a visible cold boot has been reliable:

```sh
"$SDK/emulator/emulator" -avd Pixel_6a -no-snapshot -no-audio -no-boot-anim
```

Launch that as a managed background process and retain its log/process identity. Select its serial explicitly with `adb -s SERIAL` when more than one device is present. Wait for `sys.boot_completed=1`, wake/unlock it (`input keyevent 224`, `wm dismiss-keyguard`), and verify it is awake. Headless or quick-boot state previously produced black/locked screenshots and missing Compose hierarchies; diagnose that before retrying UI tests blindly.

If a longer screen timeout is needed, save its original value and restore it at cleanup. Do not change unrelated device settings or stop another task's emulator.

Install the built app and test APK, then run the relevant test classes/methods:

```sh
"$SDK/platform-tools/adb" -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
"$SDK/platform-tools/adb" -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$SDK/platform-tools/adb" -s SERIAL shell am instrument -w \
  -e class PACKAGE.RelevantUiTest \
  com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
```

Replace the serial and test class with observed values. Grant notification permission only when the test needs it and the test device is controlled for this task. For reminder changes, exercise the real worker/scheduler when feasible, not just a static settings fixture. Separate immediate worker verification from exact wall-clock delivery, which WorkManager does not guarantee.

## Screenshot capture

Use `adb exec-out screencap -p` for manual app interaction. In Compose instrumentation, wait for idle and any native window entrance animation, then use `InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()` for full-window evidence, including platform dialogs and sheets. Write PNGs to the app cache, then extract them with `adb exec-out run-as com.example.zar cat ...` using binary-safe output. A root Compose capture can omit native dialogs or match multiple roots.

Use fixture dates valid for the current app clock, and make error/save flags correspond to reachable real states. Inspect images before committing them. Do not alter screenshot pixels to imitate a successful run. After UI edits, rerun capture for affected states. Stop only the emulator you started and remove only your clean review worktree after the pushed/merged result is verified.

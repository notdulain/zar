#!/usr/bin/env bash
# Proves an offline custom category + expense survive a real process death and reach the server once.
# Needs: Android emulator booted, `firebase emulators:start --only auth,firestore --project demo-zar` running, and the
# debug + androidTest APKs installed with -Pzar.useEmulators=true (./gradlew :app:installDebug :app:installDebugAndroidTest).
set -euo pipefail
ADB="${ADB:-adb}"
RUN="$(uuidgen | tr -d - | tr 'A-Z' 'a-z' | cut -c1-12)"
RUNNER=com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
CLASS=com.example.zar.features.transactions.OfflineRestartTest

phase() {
  "$ADB" shell am instrument -w -e class "$CLASS#$1" -e restartPhase "$2" -e restartRun "$RUN" "$RUNNER" | tee "/tmp/offline-restart-phase$2.txt"
  grep -q '^OK (1 test)' "/tmp/offline-restart-phase$2.txt"
}

echo "run $RUN"
phase processRestartPhase1WritesOffline 1
"$ADB" shell am force-stop com.example.zar   # the process dies with the writes still unsent
phase processRestartPhase2ReadsThenDeliversThem 2
echo "PROCESS RESTART OK (run $RUN)"

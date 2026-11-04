#!/bin/bash
# Emulator-only display acceptance. Refuses physical devices and restores settings on exit.
set -euo pipefail
serial="${1:-emulator-5556}"
case "$serial" in emulator-*) ;; *) echo 'An emulator serial is required' >&2; exit 2;; esac
adb_bin="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/platform-tools/adb"
out_dir="${2:-docs/evidence/task6/display}"
mkdir -p "$out_dir"
original_font=$("$adb_bin" -s "$serial" shell settings get system font_scale | tr -d '\r')
original_animator=$("$adb_bin" -s "$serial" shell settings get global animator_duration_scale | tr -d '\r')
original_rotate=$("$adb_bin" -s "$serial" shell settings get system accelerometer_rotation | tr -d '\r')
original_orientation=$("$adb_bin" -s "$serial" shell settings get system user_rotation | tr -d '\r')
restore_setting() {
  if [ "$3" = null ]; then "$adb_bin" -s "$serial" shell settings delete "$1" "$2" >/dev/null;
  else "$adb_bin" -s "$serial" shell settings put "$1" "$2" "$3" >/dev/null; fi
}
restore() {
  restore_setting system font_scale "$original_font"
  restore_setting global animator_duration_scale "$original_animator"
  restore_setting system accelerometer_rotation "$original_rotate"
  restore_setting system user_rotation "$original_orientation"
  "$adb_bin" -s "$serial" shell wm size reset >/dev/null
}
trap restore EXIT
trap 'exit 130' INT TERM
capture() {
  "$adb_bin" -s "$serial" shell am start -S -W -n com.example.zar/.MainActivity >/dev/null
  ready=false
  for attempt in $(seq 1 15); do
    "$adb_bin" -s "$serial" shell rm -f /sdcard/zar-task6-window.xml
    "$adb_bin" -s "$serial" shell uiautomator dump /sdcard/zar-task6-window.xml >/dev/null 2>&1 || true
    snapshot=$("$adb_bin" -s "$serial" shell cat /sdcard/zar-task6-window.xml 2>/dev/null || true)
    if [[ "$snapshot" == *"RECEIVED THIS MONTH"* ]]; then ready=true; break; fi
    sleep 1
  done
  if [ "$ready" != true ]; then echo "Home did not become ready: $1" >&2; return 1; fi
  "$adb_bin" -s "$serial" exec-out screencap -p > "$out_dir/$1.png"
}
for scale in 1.3 1.5 2.0; do
  "$adb_bin" -s "$serial" shell settings put system font_scale "$scale"
  capture "home-font-$scale"
done
"$adb_bin" -s "$serial" shell settings put system font_scale 1.0
"$adb_bin" -s "$serial" shell wm size 840x1860
capture home-compact-320dp
"$adb_bin" -s "$serial" shell wm size reset
"$adb_bin" -s "$serial" shell settings put system accelerometer_rotation 0
"$adb_bin" -s "$serial" shell settings put system user_rotation 1
capture home-landscape
"$adb_bin" -s "$serial" shell settings put system user_rotation 0
"$adb_bin" -s "$serial" shell settings put global animator_duration_scale 0
capture home-animator-zero

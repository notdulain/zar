#!/usr/bin/env bash
# Reproducible motion benchmark run on one attached device: capabilities -> build + install -> local emulators and seed
# -> (adb reverse) -> :benchmark -> traces -> summaries. It records what the device reports and what the traces show;
# it never forces a refresh rate and says nothing about 120 Hz unless the device reports that mode.
#
# Usage: tools/run-motion-benchmark.sh <adb-serial> [output-dir]
#
# Needs: the Firebase emulators running on the host (`firebase emulators:start --only auth,firestore`), node, a python3
# with `pip install perfetto` (PYTHON=<path> to choose one), and JDK 21 for Gradle.
#
# How the device reaches the host's emulators (zar.emulatorHost, see app/build.gradle.kts):
#   * Android emulator (serial emulator-*): 10.0.2.2, no tunnel. Default.
#   * physical phone: 127.0.0.1 through `adb reverse tcp:9099` / `tcp:8080`. Chosen automatically; the rules are removed
#     when the script ends.
#   * ZAR_EMULATOR_HOST=127.0.0.1 forces the tunnel path on an Android emulator, to prove it works without a phone.
#     (10.0.2.2 | 127.0.0.1 | localhost are the only accepted hosts: the debug/benchmark builds allow cleartext to those.)
#   * ZAR_BENCHMARK_FILTER=com.example.zar.benchmark.ZarBenchmark#homeHeroScrollFrameTiming runs one journey (or class)
#     instead of all of them, for a quick check of the whole pipeline. Unset means every journey.
set -euo pipefail

SERIAL="${1:?usage: tools/run-motion-benchmark.sh <adb-serial> [output-dir]}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(git -C "$ROOT" rev-parse --short HEAD)"
OUT="${2:-$ROOT/build/motion-benchmark/$STAMP-$SERIAL}"
ADB="${ADB:-adb}"
PYTHON="${PYTHON:-python3}"
A=("$ADB" -s "$SERIAL")
mkdir -p "$OUT"

if [[ "$SERIAL" == emulator-* ]]; then DEFAULT_HOST=10.0.2.2; else DEFAULT_HOST=127.0.0.1; fi
HOST="${ZAR_EMULATOR_HOST:-$DEFAULT_HOST}"
case "$HOST" in 10.0.2.2|127.0.0.1|localhost) ;; *) echo "ZAR_EMULATOR_HOST must be 10.0.2.2, 127.0.0.1 or localhost" >&2; exit 2;; esac

"$PYTHON" -c "import perfetto" 2>/dev/null || { echo "$PYTHON has no 'perfetto' module: pip install perfetto (or set PYTHON=<venv python>)" >&2; exit 2; }
for port in 9099 8080; do
  nc -z 127.0.0.1 "$port" || { echo "nothing listens on 127.0.0.1:$port: start the Firebase emulators first" >&2; exit 2; }
done

echo "== 1. device capabilities"
"$ROOT/tools/refresh-capabilities.sh" "$SERIAL" | tee "$OUT/device-capabilities.json"

REVERSED=0
cleanup() { if [[ $REVERSED == 1 ]]; then "${A[@]}" reverse --remove tcp:9099 >/dev/null 2>&1 || true; "${A[@]}" reverse --remove tcp:8080 >/dev/null 2>&1 || true; fi; }
trap cleanup EXIT
if [[ "$HOST" != 10.0.2.2 ]]; then
  echo "== 2. adb reverse (device $HOST:9099/8080 -> host)"
  "${A[@]}" reverse tcp:9099 tcp:9099
  "${A[@]}" reverse tcp:8080 tcp:8080
  REVERSED=1
  "${A[@]}" reverse --list | tee "$OUT/adb-reverse.txt"
else
  echo "== 2. no tunnel: the device reaches the host at 10.0.2.2"
fi

echo "== 3. seed the demo account on the local emulators"
node "$ROOT/tools/benchmark-seed.mjs"

echo "== 4. build, install and run the benchmark (host $HOST)"
cd "$ROOT"
# Results are written per device name; clear the previous ones so a stale file can never be collected as this run's.
rm -rf "$ROOT/benchmark/build/outputs/connected_android_test_additional_output"
FILTER=()
[[ -z "${ZAR_BENCHMARK_FILTER:-}" ]] || FILTER=("-Pandroid.testInstrumentationRunnerArguments.class=$ZAR_BENCHMARK_FILTER")
# ZAR_APP_ID_SUFFIX=.perf measures a side-by-side com.example.zar.perf build, so a phone's real app and data are never
# replaced or uninstalled by the connected-test cleanup. Unset means the plain com.example.zar package.
SUFFIX=()
if [[ -n "${ZAR_APP_ID_SUFFIX:-}" ]]; then
  SUFFIX=(-Pzar.appIdSuffix="$ZAR_APP_ID_SUFFIX" -Pandroid.testInstrumentationRunnerArguments.zarTargetPackage="com.example.zar$ZAR_APP_ID_SUFFIX")
fi
ANDROID_SERIAL="$SERIAL" ./gradlew --max-workers=2 :benchmark:connectedBenchmarkAndroidTest \
  -Pzar.useEmulators=true -Pzar.emulatorHost="$HOST" ${SUFFIX[@]+"${SUFFIX[@]}"} ${FILTER[@]+"${FILTER[@]}"} 2>&1 | tee "$OUT/gradle.log"

echo "== 5. collect results"
RESULTS="$(find "$ROOT/benchmark/build/outputs/connected_android_test_additional_output" -name '*benchmarkData.json' 2>/dev/null | head -1)"
[[ -n "$RESULTS" ]] || { echo "no benchmarkData.json found under benchmark/build/outputs/connected_android_test_additional_output" >&2; exit 1; }
RESULT_DIR="$(dirname "$RESULTS")"
cp "$RESULTS" "$OUT/benchmarkData.json"
mkdir -p "$OUT/traces"
find "$RESULT_DIR" -name '*.perfetto-trace' -exec cp {} "$OUT/traces/" \;
"$PYTHON" "$ROOT/tools/summarize-benchmark.py" "$OUT/benchmarkData.json" | tee "$OUT/summary.md"
if ls "$OUT"/traces/*.perfetto-trace >/dev/null 2>&1; then
  "$PYTHON" "$ROOT/tools/trace-frame-summary.py" "$OUT"/traces/*.perfetto-trace | tee "$OUT/trace-frame-summary.txt"
fi

echo "done: $OUT"
echo "Read device-capabilities.json first: a 120 Hz claim needs a physical device whose cadences list 120Hz: true AND a trace-frame-summary showing frames produced at that cadence."

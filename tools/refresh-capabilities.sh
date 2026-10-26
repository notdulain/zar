#!/usr/bin/env bash
# Records what a device reports about display cadence and the policies that can lower it, as JSON on stdout.
# It states capability and policy only: the cadence an app actually rendered at comes from a trace
# (tools/trace-frame-summary.py). A 120 Hz result needs a physical device that reports a 120 Hz mode here.
# Usage: tools/refresh-capabilities.sh [adb-serial]
set -euo pipefail
ADB="${ADB:-adb}"
SERIAL_ARGS=()
if [[ $# -ge 1 ]]; then SERIAL_ARGS=(-s "$1"); fi
sh() { "$ADB" "${SERIAL_ARGS[@]}" shell "$@" | tr -d '\r'; }

display="$(sh dumpsys display)"
export DISPLAY_DUMP="$display"
export MODEL="$(sh getprop ro.product.model)" API="$(sh getprop ro.build.version.sdk)" RELEASE="$(sh getprop ro.build.version.release)"
export FINGERPRINT="$(sh getprop ro.build.fingerprint)" EMULATOR="$(sh getprop ro.kernel.qemu)"
export PEAK="$(sh settings get system peak_refresh_rate)" MIN="$(sh settings get system min_refresh_rate)"
export LOW_POWER="$(sh settings get global low_power)" ANIMATOR="$(sh settings get global animator_duration_scale)"
export THERMAL="$(sh dumpsys thermalservice | grep -m1 -i 'Thermal Status' || true)"
python3 - <<'PY'
import json, os, re
d = os.environ["DISPLAY_DUMP"]
# Modes come from the physical display's own list, so other "fps=" text in the dump cannot add a cadence. The same
# rate listed for several resolutions counts once, and 59.99/60.000004 style values are reported as the rate they mean.
block = re.search(r"DisplayDeviceInfo\{.*?supportedModes \[(.*?)\],\s*(?:colorMode|defaultModeId|userPreferredModeId)", d, re.S)
mode_text = block.group(1) if block else d
modes = sorted({round(float(m), 1) for m in re.findall(r"\bfps=([0-9.]+)", mode_text)})
active = re.search(r"mActiveRenderFrameRate=([0-9.]+)", d)
rates = re.search(r"supportedRefreshRates \[([^\]]*)\]", d)
arr = re.search(r"hasArrSupport (true|false)", d)
supported = lambda hz: any(abs(m - hz) < 1.5 for m in modes)
emulator = os.environ["EMULATOR"] == "1" or "sdk_gphone" in os.environ["FINGERPRINT"] or "emu" in os.environ["FINGERPRINT"]
out = {
    "model": os.environ["MODEL"], "android": os.environ["RELEASE"], "api": int(os.environ["API"] or 0),
    "fingerprint": os.environ["FINGERPRINT"], "emulator": emulator,
    "supportedModeRefreshHz": modes, "activeRenderFrameRateHz": float(active.group(1)) if active else None,
    "supportedRefreshRates": [float(x) for x in rates.group(1).split(",")] if rates and rates.group(1).strip() else [],
    "hasAdaptiveRefreshRateSupport": (arr.group(1) == "true") if arr else None,
    "userPeakRefreshRate": os.environ["PEAK"], "userMinRefreshRate": os.environ["MIN"],
    "batterySaver(low_power)": os.environ["LOW_POWER"], "animatorDurationScale": os.environ["ANIMATOR"],
    "thermal": os.environ["THERMAL"].strip(),
}
# Each cadence the plan cares about, reported as present or absent. Nothing here forces or hints a rate.
out["cadences"] = {f"{hz}Hz": supported(hz) for hz in (60, 90, 120)}
out["supports120Hz"] = out["cadences"]["120Hz"]
peak = os.environ["PEAK"]
out["warnings"] = []
if peak not in ("null", "") and re.fullmatch(r"[0-9.]+", peak) and float(peak) < 119 and out["supports120Hz"]:
    out["warnings"].append(f"peak_refresh_rate={peak} will hold this 120 Hz panel below 120 Hz; restore its default, do not force a value")
if os.environ["LOW_POWER"] == "1":
    out["warnings"].append("battery saver is on and usually caps the refresh rate")
out["verdict"] = ("120 Hz mode reported: a physical 120 Hz run is possible" if out["supports120Hz"] and not emulator
                  else "no physical 120 Hz mode here: any 120 Hz result stays pending")
print(json.dumps(out, indent=2))
PY

#!/usr/bin/env python3
"""Pager swipe / Home scroll frame profile of an ISOLATED perf install, one explicit adb serial, Perfetto + gfxinfo.

    python3 tools/pager-profile.py <adb-serial> <out-dir> [--package com.example.zar.perf]

Safety (this is why it exists next to record-motion.py, whose PKG is the real app id):
- the package must end in `.perf` (build with -Pzar.appIdSuffix=.perf), so the user's real com.example.zar install, its data and
  its App Check registration are never touched; the script refuses anything else,
- every adb call is `adb -s <serial>`; no uninstall, clear-data, sign-out or settings change; nothing is written to the ledger
  except the demo user's local Firebase EMULATOR data (adb reverse 9099/8080 are added by the caller and removed after),
- no logcat is read.
Scenarios: cold = the first swipe after process start (Home is up and idle for 1.5 s first); warm = six swipes back and forth after
two warm-up round trips; home-scroll = Home collapse scroll. Each is one Perfetto trace (frame timeline + gfx/view/input/sched
atrace for the app) and a `dumpsys gfxinfo` reset/read, so app-missed frames can be told from presentation noise
(tools/trace-frame-summary.py) and cross-checked.
"""
import argparse, importlib.util, re, subprocess, sys, time
from pathlib import Path

spec = importlib.util.spec_from_file_location("rm", Path(__file__).with_name("record-motion.py"))
rm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rm)

CONFIG = """
buffers: {{ size_kb: 65536 fill_policy: RING_BUFFER }}
buffers: {{ size_kb: 8192 fill_policy: RING_BUFFER }}
data_sources: {{ config {{ name: "linux.ftrace" target_buffer: 0 ftrace_config {{
  ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_wakeup" ftrace_events: "power/cpu_frequency"
  atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input" atrace_categories: "wm" atrace_categories: "am"
  atrace_categories: "res" atrace_categories: "dalvik" atrace_categories: "sched" atrace_categories: "freq"
  atrace_apps: "{pkg}" }} }} }}
data_sources: {{ config {{ name: "android.surfaceflinger.frametimeline" target_buffer: 1 }} }}
data_sources: {{ config {{ name: "linux.process_stats" target_buffer: 1 process_stats_config {{ scan_all_processes_on_start: true }} }} }}
duration_ms: {ms}
"""


class Prof(rm.Dev):
    def __init__(self, serial, pkg, out):
        super().__init__(serial)
        self.pkg, self.out = pkg, Path(out)
        rm.PKG = pkg
        self.out.mkdir(parents=True, exist_ok=True)

    def launch(self):
        self.sh("shell", "am", "force-stop", self.pkg)
        self.sh("shell", "am", "start", "-W", "-n", f"{self.pkg}/com.example.zar.MainActivity")
        if self.wait(6, text="Sign in") or self.wait(1, text="Sign in to\nyour account"):
            self.sign_in()
        if not self.wait(40, text="RECEIVED THIS MONTH"):
            raise RuntimeError("Home never appeared (seed the demo user, adb reverse 9099/8080, build with -Pzar.emulatorHost=127.0.0.1)")

    def trace_start(self, name, ms):
        remote = f"/data/misc/perfetto-traces/{name}.pftrace"
        self.sh("shell", "rm", "-f", remote, check=False)
        cfg = CONFIG.format(pkg=self.pkg, ms=ms)
        p = subprocess.Popen(["adb", "-s", self.serial, "shell", "perfetto", "-c", "-", "--txt", "-o", remote],
                             stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True)
        p.stdin.write(cfg); p.stdin.close()
        time.sleep(2.5)  # data sources come up
        return p, remote

    def trace_stop(self, p, remote, name):
        p.wait(timeout=120)
        dest = self.out / f"{name}.perfetto-trace"
        self.sh("pull", remote, str(dest))
        self.sh("shell", "rm", "-f", remote, check=False)
        return dest

    def gfx_reset(self):
        self.sh("shell", "dumpsys", "gfxinfo", self.pkg, "reset", check=False)

    def gfx(self, name):
        txt = self.sh("shell", "dumpsys", "gfxinfo", self.pkg, check=False)
        (self.out / f"{name}-gfxinfo.txt").write_text("".join(
            l + "\n" for l in txt.splitlines() if re.search(r"Total frames|Janky|percentile|Number (Missed|High|Slow|Frame)|Legacy|GPU", l)))
        return txt

    def swipe_h(self, direction, ms=260):
        a, b = (.85, .15) if direction > 0 else (.15, .85)
        self.swipe(self.w * a, self.h * .45, self.w * b, self.h * .45, ms)

    def drag_y(self, frm, to, ms=500):
        self.swipe(self.w / 2, self.h * frm, self.w / 2, self.h * to, ms)


def run(d, name, body, ms=22000):
    p, remote = d.trace_start(name, ms)
    d.gfx_reset()
    body()
    time.sleep(1.0)
    d.gfx(name)
    return d.trace_stop(p, remote, name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial"); ap.add_argument("out")
    ap.add_argument("--package", default="com.example.zar.perf")
    ap.add_argument("--only", default="cold,warm,home-scroll,taps,child")
    ap.add_argument("--repeat", type=int, default=1)
    ap.add_argument("--settle", type=float, default=1.5, help="seconds idle on Home after launch before the first gesture")
    a = ap.parse_args()
    if not a.package.endswith(".perf"):
        sys.exit("refusing: the profiled package must be an isolated .perf install, never the real com.example.zar")
    d = Prof(a.serial, a.package, a.out)
    want = a.only.split(",")
    for i in range(a.repeat):
        tag = f"-{i + 1}" if a.repeat > 1 else ""; tab = tag
        if "cold" in want:
            d.launch(); time.sleep(a.settle)
            run(d, f"cold-first-swipe{tag}", lambda: (d.swipe_h(1), time.sleep(1.2)), ms=9000)
        if "warm" in want:
            d.launch(); time.sleep(a.settle)
            for _ in range(2): d.swipe_h(1); time.sleep(.8); d.swipe_h(-1); time.sleep(.8)
            def warm():
                for _ in range(3):
                    d.swipe_h(1); time.sleep(.8); d.swipe_h(-1); time.sleep(.8)
            run(d, f"warm-swipes{tag}", warm, ms=14000)
        if "home-scroll" in want:
            d.launch(); time.sleep(a.settle)
            def scroll():
                for _ in range(3): d.drag_y(.8, .3, 500); time.sleep(.4)
                for _ in range(3): d.drag_y(.3, .8, 500); time.sleep(.4)
            run(d, f"home-scroll{tag}", scroll, ms=14000)
        if "taps" in want:
            d.launch(); time.sleep(a.settle)
            d.swipe_h(1); time.sleep(1.2)  # reveals the full navbar
            spots = d.tab_spots(rm.TABS)
            def taps():
                d.tab_burst(spots, ["Plans", "More", "Activity", "Home"] * 2, gap=.15); time.sleep(1.0)
                d.tab_burst(spots, ["Plans", "Home", "More", "Activity"], gap=.6); time.sleep(1.0)
            run(d, f"nav-taps{tab}", taps, ms=12000)
        if "child" in want:
            # Open Settings from More and press Back: push and pop of a child route over the warmed tabs.
            d.launch(); time.sleep(a.settle)
            d.swipe_h(1); time.sleep(1.2)
            d.tab("More") if False else d.tap(desc="More"); time.sleep(1.2)
            if not d.find(text="Settings"):
                d.drag_y(.8, .3, 500); time.sleep(.6)
            settings = d.find(text="Settings")
            def push():
                d.tap_node(settings); time.sleep(1.6)
            run(d, f"child-push{tab}", push, ms=7000)
            run(d, f"child-pop{tab}", lambda: (d.sh("shell", "input", "keyevent", "KEYCODE_BACK"), time.sleep(1.6)), ms=7000)
    print("traces in", a.out)


if __name__ == "__main__":
    main()

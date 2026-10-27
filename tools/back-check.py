#!/usr/bin/env python3
"""Back-behaviour recordings on an ISOLATED `.perf` install of the running app (one explicit adb serial, synthetic emulator data).

    python3 tools/back-check.py <adb-serial> <out-dir> [--package com.example.zar.perf] [--only name,name]

Refuses any package that does not end in `.perf`, so the user's real com.example.zar install is never launched, force-stopped or
written. Each scenario records the screen (screenrecord) and writes a contact sheet of frames (ffmpeg) next to it. A recording
is evidence of what moved, not a timing.
Scenarios: tab-button (Plans -> Back button), tab-edge (More -> edge drag cancel, then commit), child-button (Settings),
child-edge (Settings edge cancel then commit), add-sheet, quick-note, income-sheet.
"""
import argparse, importlib.util, subprocess, sys, time
from pathlib import Path

spec = importlib.util.spec_from_file_location("rm", Path(__file__).with_name("record-motion.py"))
rm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rm)


class B(rm.Dev):
    def __init__(self, serial, pkg, out):
        super().__init__(serial)
        self.pkg, self.out = pkg, Path(out)
        rm.PKG = pkg
        self.out.mkdir(parents=True, exist_ok=True)

    def launch(self):
        self.sh("shell", "am", "force-stop", self.pkg)
        self.sh("shell", "am", "start", "-W", "-n", f"{self.pkg}/com.example.zar.MainActivity")
        if self.wait(6, text="Sign in"):
            self.sign_in()
        if not self.wait(40, text="RECEIVED THIS MONTH"):
            raise RuntimeError("Home never appeared")
        time.sleep(3.5)  # the idle warm-up composes the other tabs

    def go(self, name):
        n = self.find(desc=name)
        if n: self.tap_node(n)
        else: self.tap(text=name)
        time.sleep(1.2)

    def key_back(self):
        self.sh("shell", "input", "keyevent", "KEYCODE_BACK")

    def edge(self, to_x, release=True, hold=0.0, y=0.5, side="left"):
        y = int(self.h * y)
        x0 = 2 if side == "left" else self.w - 2
        sign = 1 if side == "left" else -1
        ev = lambda a, x: self.sh("shell", "input", "motionevent", a, str(x), str(y))
        ev("DOWN", x0)
        for x in range(30, int(to_x), 30): ev("MOVE", x0 + sign * x)
        if hold: time.sleep(hold)
        return ev

    def sheet(self, name):
        sheet = self.out / f"{name}.png"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(self.out / f"{name}.mp4"), "-vf",
                        "fps=12,scale=180:-1,tile=8x3", "-frames:v", "1", str(sheet)], check=False)

    def record(self, name, body):
        with rm.Recording(self, self.out / f"{name}.mp4"):
            time.sleep(.5)
            body()
        self.sheet(name)


def tab_button(d):
    d.launch(); d.go("Plans")
    d.record("tab-button", lambda: (d.key_back(), time.sleep(1.4)))


def tab_edge(d):
    d.launch(); d.go("More")
    def body():
        ev = d.edge(420, hold=.4)
        for x in range(390, 1, -60): ev("MOVE", x)
        ev("UP", 2); time.sleep(1.0)           # cancel: springs back
        ev = d.edge(540)
        ev("UP", 540); time.sleep(1.6)         # commit
    d.record("tab-edge", body)


def open_settings(d):
    d.go("More")
    if not d.find(text="Settings"):
        d.swipe(d.w / 2, d.h * .8, d.w / 2, d.h * .3, 500); time.sleep(.6)
    d.tap(text="Settings"); time.sleep(1.2)


def child_button(d):
    d.launch()
    d.record("child-open-chrome", lambda: (open_settings(d), time.sleep(.5)))
    d.record("child-button", lambda: (d.key_back(), time.sleep(1.4)))


def child_edge(d):
    d.launch(); open_settings(d)
    def body():
        ev = d.edge(420, hold=.4)
        for x in range(390, 1, -60): ev("MOVE", x)
        ev("UP", 2); time.sleep(1.0)
        ev = d.edge(540)
        ev("UP", 540); time.sleep(1.6)
    d.record("child-edge", body)


def add_sheet(d, label, name, wait_text):
    d.launch(); d.go("Home")
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text=label); d.wait(8, text=wait_text); time.sleep(1.0)
    d.record(name + "-button", lambda: (d.key_back(), time.sleep(1.4)))


def add_edge(d):
    d.launch(); d.go("Home")
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text="MANUAL ENTRY"); d.wait(8, text="Add transaction"); time.sleep(1.0)
    def body():
        ev = d.edge(420, hold=.4)
        for x in range(390, 1, -60): ev("MOVE", x)
        ev("UP", 2); time.sleep(1.0)
        ev = d.edge(540); ev("UP", 540); time.sleep(1.6)
    d.record("add-sheet-edge", body)


SCENARIOS = {
    "tab-button": tab_button, "tab-edge": tab_edge, "child-button": child_button, "child-edge": child_edge,
    "add-sheet": lambda d: add_sheet(d, "MANUAL ENTRY", "add-sheet", "Add transaction"),
    "add-edge": add_edge,
    "quick-note": lambda d: add_sheet(d, "QUICK NOTE", "quick-note", "Quick note"),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial"); ap.add_argument("out")
    ap.add_argument("--package", default="com.example.zar.perf")
    ap.add_argument("--only", default=",".join(SCENARIOS))
    a = ap.parse_args()
    if not a.package.endswith(".perf"):
        sys.exit("refusing: use an isolated .perf install, never the real com.example.zar")
    d = B(a.serial, a.package, a.out)
    for name in a.only.split(","):
        print("scenario", name, flush=True)
        SCENARIOS[name](d)
    print("recordings in", a.out)


if __name__ == "__main__":
    main()

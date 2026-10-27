#!/usr/bin/env python3
"""Round-2 evidence from the RUNNING debug app on ONE explicit adb serial (adb -s every call), synthetic data only.

    python3 tools/record-round2.py emulator-5554 docs/evidence/pixel6a-feedback/round2 [--theme light|dark|both]

Preconditions (nothing here bypasses authentication):
- the debug app was built with -Pzar.useEmulators=true and BuildConfig.USE_FIREBASE_EMULATORS is true (checked below),
- the LOCAL Firebase Auth (9099) and Firestore (8080) emulators run and `node tools/benchmark-seed.mjs` seeded the demo user,
- the device is the emulator: this refuses any serial that is not `emulator-*`, so the physical phone is never touched.
It signs in through the real sign-in screen. Screenshots are `screencap`, recordings `screenrecord`; no frame figure is
taken from a recording. The theme is set by writing the app's own display-preferences file, and system animation scale is
restored to 1 at the end.
"""
import argparse, importlib.util, re, subprocess, sys, time
from pathlib import Path

spec = importlib.util.spec_from_file_location("rm", Path(__file__).with_name("record-motion.py"))
rm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rm)
PKG, EMAIL, PASSWORD = rm.PKG, rm.EMAIL, rm.PASSWORD


class D(rm.Dev):
    def shot(self, name):
        time.sleep(.8)
        png = subprocess.run(["adb", "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
        (self.out / f"{name}-{self.theme}.png").write_bytes(png)

    def launch(self):
        self.sh("shell", "am", "force-stop", PKG)
        self.sh("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
        if self.wait(6, text="Sign in to\nyour account"):
            self.sign_in2()
        if not self.wait(30, text="RECEIVED THIS MONTH"):
            raise RuntimeError("Home never appeared")
        time.sleep(1.2)

    def sign_in2(self):
        edits = [n for n in self.nodes() if n["cls"].endswith("EditText")]
        for n, value in zip(edits, (EMAIL, PASSWORD)):
            self.tap_node(n); time.sleep(.3)
            self.sh("shell", "input", "text", value.replace("@", "\\@")); time.sleep(.3)
        self.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        self.tap(5, text="Sign in")

    def go(self, name):
        """Open a tab: its navbar item (content description) when the navbar is up, else the Home card of that name."""
        n = self.find(desc=name)
        if n: self.tap_node(n)
        else: self.tap(text=name)
        time.sleep(1.1)

    def key_back(self):
        self.sh("shell", "input", "keyevent", "KEYCODE_BACK")

    def drag_y(self, frm, to, ms=500):
        self.swipe(self.w / 2, self.h * frm, self.w / 2, self.h * to, ms)


def scenario_home_collapse(d):
    d.launch(); d.shot("home-rest")
    for i in range(2):
        d.drag_y(.8, .55, 700); time.sleep(.4)
        d.shot(f"home-scroll-{i + 1}")
    d.drag_y(.85, .15, 500); time.sleep(.3); d.drag_y(.85, .15, 500); time.sleep(.5); d.shot("home-end-floating-nav")
    with rm.Recording(d, d.out / f"home-collapse-{d.theme}.mp4"):
        for _ in range(3): d.drag_y(.15, .85, 500); time.sleep(.4)
        for _ in range(3): d.drag_y(.85, .15, 500); time.sleep(.4)


def scenario_nav_and_pager(d):
    d.launch()
    d.go("Activity"); d.shot("nav-activity-selected")
    spots = d.tab_spots(rm.TABS)
    with rm.Recording(d, d.out / f"nav-stationary-taps-{d.theme}.mp4"):
        time.sleep(.5); d.tab_burst(spots, ["Plans", "More", "Activity", "Home"] * 2, gap=.15); time.sleep(1.0)
    with rm.Recording(d, d.out / f"pager-swipe-{d.theme}.mp4"):
        for _ in range(3):
            d.swipe(d.w * .85, d.h * .45, d.w * .15, d.h * .45, 260); time.sleep(.7)
        for _ in range(3):
            d.swipe(d.w * .15, d.h * .45, d.w * .85, d.h * .45, 260); time.sleep(.7)
    d.shot("pager-after-swipes")


def open_settings(d):
    if not d.find(text="Settings"):
        d.drag_y(.8, .3, 500); time.sleep(.6)
    d.tap(text="Settings"); time.sleep(1.2)


def scenario_back(d):
    d.launch(); d.go("More")
    open_settings(d); d.shot("settings-child")
    with rm.Recording(d, d.out / f"back-child-button-{d.theme}.mp4"):
        time.sleep(.5); d.key_back(); time.sleep(1.4)
    open_settings(d)
    y = int(d.h * .5)
    ev = lambda a, x: d.sh("shell", "input", "motionevent", a, str(x), str(y))
    with rm.Recording(d, d.out / f"back-child-edge-{d.theme}.mp4"):
        time.sleep(.5)
        ev("DOWN", 2)
        for x in range(30, 421, 30): ev("MOVE", x)
        time.sleep(.5)
        for x in range(390, 1, -60): ev("MOVE", x)
        ev("UP", 2); time.sleep(1.0)
        ev("DOWN", 2)
        for x in range(30, 541, 30): ev("MOVE", x)
        ev("UP", 540); time.sleep(1.4)
    d.go("Home")
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text="MANUAL ENTRY"); d.wait(5, text="Add transaction"); time.sleep(.8); d.shot("add-sheet")
    with rm.Recording(d, d.out / f"back-sheet-button-{d.theme}.mp4"):
        time.sleep(.5); d.key_back(); time.sleep(1.4)


def scenario_plans(d):
    d.launch(); d.go("Plans"); d.shot("plans")
    d.tap(text="New plan"); time.sleep(1.0); d.shot("plans-new-plan-sheet")
    d.key_back(); time.sleep(1.0)
    d.drag_y(.85, .15, 500); d.drag_y(.85, .15, 500); time.sleep(.5); d.shot("plans-end-floating-nav")


def scenario_auth(d):
    d.sh("shell", "am", "force-stop", PKG)
    d.sh("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    if not d.wait(8, text="Sign in to\nyour account"):
        print("already signed in: sign-out is not driven here (no data reset); auth frames skipped"); return
    d.shot("auth-idle")
    edits = [n for n in d.nodes() if n["cls"].endswith("EditText")]
    d.tap_node(edits[0]); d.sh("shell", "input", "text", "synthetic\\@zar.test"); time.sleep(.4)
    d.tap_node([n for n in d.nodes() if n["cls"].endswith("EditText")][1]); d.sh("shell", "input", "text", "wrong-password")
    time.sleep(.6); d.shot("auth-keyboard")
    d.key_back(); d.tap(5, text="Sign in"); d.wait(8, pattern=r"Email or password is incorrect.*"); d.shot("auth-error")


SCENARIOS = {"auth": scenario_auth, "home-collapse": scenario_home_collapse, "nav-pager": scenario_nav_and_pager, "back": scenario_back,
             "plans": scenario_plans}


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("serial"); ap.add_argument("out")
    ap.add_argument("--theme", default="both"); ap.add_argument("--only", default="")
    a = ap.parse_args()
    if not a.serial.startswith("emulator-"):
        sys.exit("refusing a non-emulator serial: the physical phone is never driven by this script")
    d = D(a.serial); d.out = Path(a.out); d.out.mkdir(parents=True, exist_ok=True)
    wanted = [s for s in a.only.split(",") if s] or list(SCENARIOS)
    try:
        for theme in (["light", "dark"] if a.theme == "both" else [a.theme]):
            d.theme = theme; d.set_theme(theme)
            for name in wanted:
                print(f"== {theme}: {name}", flush=True); SCENARIOS[name](d)
    finally:
        d.animations(1); d.set_theme("light"); d.sh("shell", "am", "force-stop", PKG, check=False)


if __name__ == "__main__":
    main()

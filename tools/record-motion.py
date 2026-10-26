#!/usr/bin/env python3
"""Scripted motion recordings of the running Zar debug app on one emulator or device (adb screenrecord + input).

    python3 tools/record-motion.py <adb-serial> <out-dir> [--theme light|dark|both] [--only name,name]

Needs the debug app installed (built with -Pzar.useEmulators=true), the demo account seeded on the LOCAL Firebase
emulators (`node tools/benchmark-seed.mjs`) and a device on gesture navigation. It signs in through the real sign-in
screen with the benchmark demo credentials, so nothing is bypassed and no live service is contacted. The theme is
chosen by writing the app's own display-preferences file (the setting Settings > Appearance writes), and the
animator scale / reduce-motion state is restored to normal when the run ends.

These are recordings, not timings: screenrecord adds load, so no frame figure is taken from them.
"""
import argparse, re, shlex, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.example.zar"
EMAIL, PASSWORD = "benchmark@zar.test", "benchmark-pass-1"


class Dev:
    def __init__(self, serial):
        self.serial = serial
        self.w, self.h = self._size()

    def sh(self, *args, check=True):
        r = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True)
        if check and r.returncode:
            raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip()}")
        return r.stdout

    def _size(self):
        m = re.search(r"(\d+)x(\d+)", self.sh("shell", "wm", "size"))
        return int(m.group(1)), int(m.group(2))

    # --- UI lookup -------------------------------------------------------------------------
    def nodes(self):
        self.sh("shell", "uiautomator", "dump", "/sdcard/zar-ui.xml", check=False)
        raw = self.sh("shell", "cat", "/sdcard/zar-ui.xml", check=False)
        try:
            root = ET.fromstring(raw[raw.index("<"):])
        except Exception:
            return []
        out = []
        for n in root.iter("node"):
            b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if b:
                out.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""), "cls": n.get("class", ""),
                            "box": tuple(map(int, b.groups()))})
        return out

    def find(self, text=None, desc=None, pattern=None, lowest=False):
        hits = []
        for n in self.nodes():
            if text is not None and n["text"] != text: continue
            if desc is not None and n["desc"] != desc: continue
            if pattern is not None and not re.fullmatch(pattern, n["text"]): continue
            if text is None and desc is None and pattern is None: continue
            hits.append(n)
        if not hits: return None
        hits.sort(key=lambda n: (n["box"][1] + n["box"][3]) / 2)
        return hits[-1 if lowest else 0]

    def wait(self, timeout=10, **kw):
        end = time.time() + timeout
        while time.time() < end:
            n = self.find(**kw)
            if n: return n
            time.sleep(0.4)
        return None

    def tap_node(self, n, hold_ms=0):
        x, y = (n["box"][0] + n["box"][2]) // 2, (n["box"][1] + n["box"][3]) // 2
        if hold_ms: self.sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(hold_ms))
        else: self.sh("shell", "input", "tap", str(x), str(y))
        return n

    def tap(self, timeout=10, hold_ms=0, **kw):
        n = self.wait(timeout, **kw)
        if not n: raise RuntimeError(f"not found: {kw}")
        return self.tap_node(n, hold_ms)

    def swipe(self, x1, y1, x2, y2, ms):
        self.sh("shell", "input", "swipe", *map(str, (int(x1), int(y1), int(x2), int(y2), ms)))

    # --- app state -------------------------------------------------------------------------
    def set_theme(self, mode):
        xml = ('<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map>'
               f'<string name="appearance">{mode.upper()}</string><boolean name="reduce_motion" value="false" /></map>')
        self.sh("shell", "am", "force-stop", PKG)
        inner = f"mkdir -p shared_prefs && printf '%s' '{xml}' > shared_prefs/display_preferences.xml"
        self.sh("shell", f"run-as {PKG} sh -c {shlex.quote(inner)}")

    def animations(self, scale):
        self.sh("shell", "settings", "put", "global", "animator_duration_scale", str(scale))

    def launch_home(self):
        self.sh("shell", "am", "force-stop", PKG)
        self.sh("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
        if self.wait(4, text="SIGN IN") or self.wait(1, text="Sign in"):
            self.sign_in()
        if not self.wait(25, text="RECEIVED THIS MONTH"):
            raise RuntimeError("Home never appeared: seed the demo account and build with -Pzar.useEmulators=true")
        time.sleep(1.2)

    def sign_in(self):
        edits = [n for n in self.nodes() if n["cls"].endswith("EditText")]
        if len(edits) < 2: raise RuntimeError("sign-in fields not found")
        for n, value in zip(edits, (EMAIL, PASSWORD)):
            self.tap_node(n); time.sleep(0.3)
            self.sh("shell", "input", "text", value.replace("@", "\\@")); time.sleep(0.3)
        self.sh("shell", "input", "keyevent", "KEYCODE_BACK")
        self.tap(5, text="Sign in")

    def tab_spots(self, names):
        """Screen coordinates of the nav tabs (they carry their name as a content description), looked up once."""
        spots = {}
        for n in self.nodes():
            if n["desc"] in names:
                spots[n["desc"]] = ((n["box"][0] + n["box"][2]) // 2, (n["box"][1] + n["box"][3]) // 2)
        missing = set(names) - set(spots)
        if missing: raise RuntimeError(f"tabs not found: {missing}")
        return spots

    def tab_burst(self, spots, order, gap=0.12):
        """Tap the tabs in [order] from one shell command, so each tap lands while the capsule is still travelling."""
        self.sh("shell", f"; sleep {gap}; ".join("input tap %d %d" % spots[n] for n in order))

    def tab(self, name):
        n = self.find(text=name, lowest=True)
        if not n: raise RuntimeError(f"tab {name} not found")
        self.tap_node(n)


class Recording:
    def __init__(self, dev, path):
        self.dev, self.path = dev, path

    def __enter__(self):
        self.dev.sh("shell", "rm", "-f", "/sdcard/zar-rec.mp4", check=False)
        self.p = subprocess.Popen(["adb", "-s", self.dev.serial, "shell", "screenrecord", "--size", "540x1200",
                                   "--bit-rate", "5000000", "--time-limit", "120", "/sdcard/zar-rec.mp4"])
        time.sleep(1.0)
        return self

    def __exit__(self, *exc):
        time.sleep(0.8)
        self.dev.sh("shell", "pkill", "-2", "screenrecord", check=False)
        self.p.wait(timeout=30)
        time.sleep(1.0)
        self.dev.sh("pull", "/sdcard/zar-rec.mp4", str(self.path))
        self.dev.sh("shell", "rm", "-f", "/sdcard/zar-rec.mp4", check=False)


# --- scenarios: each starts from Home and ends wherever it likes ---------------------------------

TABS = ["Home", "Activity", "Plans", "More"]
TAB_ORDER = ["Plans", "More", "Activity", "Home"] * 2


def home_parallax(d):
    d.launch_home()
    with Recording(d, d.out / f"home-parallax-{d.theme}.mp4"):
        x = d.w / 2
        for _ in range(3):
            d.swipe(x, d.h * .78, x, d.h * .22, 260); time.sleep(.35)
        for _ in range(3):
            d.swipe(x, d.h * .22, x, d.h * .78, 260); time.sleep(.35)
        d.swipe(x, d.h * .7, x, d.h * .45, 900)  # slow drag shows the layers separating
        time.sleep(.6)


def nav_retarget(d):
    d.animations(1)
    d.launch_home()
    d.tap(text="Activity"); time.sleep(1.2)
    spots = d.tab_spots(TABS)
    with Recording(d, d.out / f"nav-capsule-retarget-{d.theme}.mp4"):
        time.sleep(.5)
        d.tab_burst(spots, TAB_ORDER)
        time.sleep(1.2)


def nav_reduced_motion(d):
    d.launch_home()
    d.tap(text="Activity"); time.sleep(1.2)
    d.animations(0)
    try:
        spots = d.tab_spots(TABS)
        with Recording(d, d.out / f"nav-capsule-reduced-motion-{d.theme}.mp4"):
            time.sleep(.5)
            d.tab_burst(spots, TAB_ORDER)
            time.sleep(1.2)
    finally:
        d.animations(1)


def section_reflow_press(d):
    d.launch_home()
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text="MANUAL ENTRY"); d.wait(5, text="Add transaction"); time.sleep(.8)
    with Recording(d, d.out / f"section-reflow-press-{d.theme}.mp4"):
        for kind in ("Income", "Transfer", "Expense", "Income", "Expense"):
            d.tap(text=kind); time.sleep(.9)
        for kind in ("Transfer", "Expense"):  # long press: the button scales down while held, returns on release
            d.tap(text=kind, hold_ms=700); time.sleep(.7)
        time.sleep(.5)
    d.sh("shell", "input", "keyevent", "KEYCODE_BACK")


def add_income_route(d):
    """Received income -> + : the entry route rises from the bottom while the list below it stays put, then pops back."""
    d.launch_home()
    d.tab("More"); time.sleep(1.0)
    d.tap(text="Received income"); d.wait(5, text="Received"); time.sleep(1.0)
    with Recording(d, d.out / f"add-income-route-{d.theme}.mp4"):
        time.sleep(.6)
        d.tap_node(d.find(text="+", lowest=True)); time.sleep(1.6)
        d.tap(text="Salary"); time.sleep(.6)
        d.sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.4)
        d.sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.2)


def add_sheet_time_dial(d):
    """Add sheet (Expense tab, whose time chip opens the dial), then the arm follows the finger around the face."""
    import math
    d.launch_home()
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text="MANUAL ENTRY"); d.wait(5, text="Add transaction"); time.sleep(.8)
    d.tap(pattern=r"\d{1,2}:\d{2} [AP]M"); time.sleep(1.0)
    twelve, six = d.find(text="12"), d.find(text="6")
    if not (twelve and six):
        raise RuntimeError("time dial numerals not found")
    cx = (twelve["box"][0] + twelve["box"][2] + six["box"][0] + six["box"][2]) / 4
    cy = (twelve["box"][1] + twelve["box"][3] + six["box"][1] + six["box"][3]) / 4
    r = (six["box"][1] - twelve["box"][1]) / 2 * .8
    with Recording(d, d.out / f"time-dial-arm-{d.theme}.mp4"):
        time.sleep(.5)
        for start in (-90, 0, 90, 180):  # four arcs of 90 degrees, hour face then (after hand-off) minute face
            pts = [(cx + r * math.cos(math.radians(start + k)), cy + r * math.sin(math.radians(start + k))) for k in range(0, 91, 10)]
            d.sh("shell", "input", "swipe", *map(str, (int(pts[0][0]), int(pts[0][1]), int(pts[-1][0]), int(pts[-1][1]), 500)))
            time.sleep(.7)
        time.sleep(.5)
    d.sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(.6)
    d.sh("shell", "input", "keyevent", "KEYCODE_BACK")


def predictive_back(d):
    """A real edge swipe through the system gesture (input motionevent, so it is held mid-way): drag, release back at
    the edge (cancel: the screen returns), then drag across and release (commit: the route pops)."""
    d.launch_home()
    d.tab("More"); time.sleep(1.0)
    d.tap(text="Received income"); d.wait(5, text="Received"); time.sleep(1.2)
    y = int(d.h * .5)
    def ev(action, x):
        d.sh("shell", "input", "motionevent", action, str(x), str(y))
    with Recording(d, d.out / f"predictive-back-edge-{d.theme}.mp4"):
        time.sleep(.5)
        ev("DOWN", 2)
        for x in range(30, 421, 30): ev("MOVE", x)
        time.sleep(.8)
        for x in range(390, 1, -60): ev("MOVE", x)
        ev("UP", 2); time.sleep(1.2)          # cancelled: still on the income list
        ev("DOWN", 2)
        for x in range(30, 541, 30): ev("MOVE", x)
        time.sleep(.5)
        ev("UP", 540); time.sleep(1.5)        # committed: back on More
        time.sleep(.5)


def screenshots(d):
    """Still frames of the running app for the PR: Home, Activity, More, the Add sheet, the time dial, the income route."""
    def shot(name):
        time.sleep(.7)
        png = subprocess.run(["adb", "-s", d.serial, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
        (d.out / f"{name}-{d.theme}.png").write_bytes(png)
    d.launch_home(); shot("home")
    d.tap(text="Activity"); time.sleep(1.2); shot("activity")
    d.tab("More"); time.sleep(1.0); shot("more")
    d.tab("Home"); time.sleep(.8)
    d.tap(desc="Add an entry"); time.sleep(.8)
    d.tap(text="MANUAL ENTRY"); d.wait(5, text="Add transaction"); time.sleep(.8); shot("add-sheet-expense")
    d.tap(pattern=r"\d{1,2}:\d{2} [AP]M"); time.sleep(1.0); shot("time-dial")
    d.sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(.5)
    d.sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(.8)
    d.launch_home(); d.tab("More"); time.sleep(1.0)
    d.tap(text="Received income"); d.wait(5, text="Received"); time.sleep(1.0)
    d.tap_node(d.find(text="+", lowest=True)); time.sleep(1.6); shot("add-income-route")


SCENARIOS = {f.__name__.replace("_", "-"): f for f in
             (home_parallax, nav_retarget, nav_reduced_motion, section_reflow_press, add_income_route, add_sheet_time_dial, predictive_back, screenshots)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("serial"); ap.add_argument("out")
    ap.add_argument("--theme", default="both", choices=["light", "dark", "both"])
    ap.add_argument("--only", default="")
    a = ap.parse_args()
    d = Dev(a.serial)
    d.out = Path(a.out); d.out.mkdir(parents=True, exist_ok=True)
    wanted = [s for s in a.only.split(",") if s] or list(SCENARIOS)
    themes = ["light", "dark"] if a.theme == "both" else [a.theme]
    try:
        for theme in themes:
            d.theme = theme
            d.set_theme(theme)
            for name in wanted:
                print(f"== {theme}: {name}", flush=True)
                SCENARIOS[name](d)
    finally:
        d.animations(1)
        d.set_theme("light")
        d.sh("shell", "am", "force-stop", PKG, check=False)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Per-iteration frame cadence and deadline attribution from Macrobenchmark Perfetto traces.

FrameTimingMetric counts a frame as late when it overran its deadline, whoever caused it. This reads the same traces and
separates frames the app itself missed (FrameTimeline "App Deadline Missed", or a UI-thread frame longer than its budget)
from presentation misses on the system side (SurfaceFlinger / display / buffer queue), and reports the cadence the app
actually produced frames at while it was animating. A 120 Hz claim needs a physical 120 Hz device: this prints what the
trace shows, nothing more.

Usage: tools/trace-frame-summary.py [--package com.example.zar] <trace> [<trace> ...]
Needs `pip install perfetto` (the trace processor downloads on first use).
"""
import argparse, collections, statistics, sys
from perfetto.trace_processor import TraceProcessor

APP_JANK = ("App Deadline Missed",)

def summarize(path, package):
    tp = TraceProcessor(trace=path)
    try:
        frames = list(tp.query(
            "select a.ts ts, a.dur/1e6 dur, a.jank_type jank from actual_frame_timeline_slice a "
            f"join process p using(upid) where p.name='{package}' order by a.ts"))
        expected = [r.dur for r in tp.query(
            "select e.dur/1e6 dur from expected_frame_timeline_slice e join process p using(upid) "
            f"where p.name='{package}'")]
        main = list(tp.query(
            "select s.dur/1e6 dur from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid) "
            f"join process p using(upid) where p.name='{package}' and t.tid=p.pid and s.name like 'Choreographer#doFrame%'"))
        do_frames = len(main)
        # Frames in which Compose recomposed anything, found by a Recomposer:recompose slice inside a doFrame.
        recomposed = list(tp.query(
            "select count(distinct d.id) c from slice d join thread_track tt on d.track_id=tt.id join thread t using(utid) "
            f"join process p using(upid) join slice r on r.track_id=d.track_id and r.ts>=d.ts and r.ts<d.ts+d.dur "
            f"where p.name='{package}' and t.tid=p.pid and d.name like 'Choreographer#doFrame%' and r.name='Recomposer:recompose'"))[0].c
    finally:
        tp.close()
    n = len(frames)
    period = statistics.median(expected) if expected else float("nan")
    gaps = [(b.ts - a.ts) / 1e6 for a, b in zip(frames, frames[1:])]
    active = [g for g in gaps if g < 2.5 * period] if expected else []
    cadence = 1000 / statistics.median(active) if active else float("nan")
    kinds = collections.Counter(f.jank for f in frames)
    app = sum(v for k, v in kinds.items() if any(a in k for a in APP_JANK))
    system = sum(v for k, v in kinds.items() if k not in ("None", "Buffer Stuffing", "Non Animating") and not any(a in k for a in APP_JANK))
    over = sum(1 for m in main if m.dur > period) if expected else 0
    worst = max((m.dur for m in main), default=0)
    return dict(recomposed=recomposed, doframes=do_frames, n=n, period=period, cadence=cadence, app=app, system=system, uiover=over, worst=worst, kinds=dict(kinds))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", default="com.example.zar")
    ap.add_argument("traces", nargs="+")
    args = ap.parse_args()
    rows = [(t, summarize(t, args.package)) for t in args.traces]
    print("| trace | frames | display period ms | active cadence Hz | app-missed | system-side late | UI frames > period | worst UI frame ms | UI frames / with recomposition |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for t, r in rows:
        name = t.rsplit("/", 1)[-1].replace(".perfetto-trace", "")
        print(f"| {name} | {r['n']} | {r['period']:.2f} | {r['cadence']:.1f} | {r['app']} | {r['system']} | {r['uiover']} | {r['worst']:.1f} | {r['doframes']} / {r['recomposed']} |")
    tot = lambda k: sum(r[k] for _, r in rows)
    n = tot("n") or 1
    print(f"| **all** | {tot('n')} | | | {tot('app')} ({100*tot('app')/n:.2f}%) | {tot('system')} ({100*tot('system')/n:.2f}%) | {tot('uiover')} | {max(r['worst'] for _, r in rows):.1f} | {tot('doframes')} / {tot('recomposed')} |")

if __name__ == "__main__":
    sys.exit(main())

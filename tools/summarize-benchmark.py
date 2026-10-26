#!/usr/bin/env python3
"""Prints a markdown table from a Macrobenchmark benchmarkData.json (frame CPU / overrun percentiles across all frames
of all iterations, plus startup and frame counts). Usage: summarize-benchmark.py <benchmarkData.json> [...]"""
import json, sys

def row(b):
    name = b["name"]
    out = [name]
    if "frameCount" in b["metrics"]:
        out.append("frames/iter min %d, med %d, max %d" % tuple(b["metrics"]["frameCount"][k] for k in ("minimum", "median", "maximum")))
    if "timeToInitialDisplayMs" in b["metrics"]:
        t = b["metrics"]["timeToInitialDisplayMs"]
        out.append("TTID ms min %.0f, med %.0f, max %.0f" % (t["minimum"], t["median"], t["maximum"]))
    for key, label in (("frameDurationCpuMs", "frame CPU ms"), ("frameOverrunMs", "overrun ms")):
        runs = (b.get("sampledMetrics") or {}).get(key)
        if runs:
            values = sorted(v for run in runs["runs"] for v in run)
            pct = lambda p: values[min(len(values) - 1, int(len(values) * p))]
            late = sum(v > 0 for v in values)
            out.append("%s p50 %.1f / p90 %.1f / p95 %.1f / p99 %.1f (n=%d%s)" % (
                label, pct(.5), pct(.9), pct(.95), pct(.99), len(values),
                ", %d late (%.1f%%)" % (late, 100 * late / len(values)) if key == "frameOverrunMs" else ""))
    return out

for path in sys.argv[1:]:
    for b in json.load(open(path))["benchmarks"]:
        print("- " + " | ".join(row(b)))

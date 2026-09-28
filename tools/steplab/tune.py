#!/usr/bin/env python3
"""Walk-versus-shake step detector (task 3.1, docs/spikes/3.1-walk-vs-shake.md, Part B).

Reference implementation of the app's AccelStepDetector, the pre-registered grid, and the pick rule.

usage:
  python tune.py pick  <data-dir>                 grid over dev files, prints the table, writes rule.json
  python tune.py score <data-dir> <prefix>        counts steps per file (e.g. prefix "held-") with rule.json
  python tune.py counts <data-dir>                per-file counts with rule.json, as JSON (for the Kotlin parity test)

Files are StepLabActivity CSVs: t_ms,type,x,y,z with type a (accelerometer), g (gyroscope), d, c (Android step
sensors, ignored here). Raw data stays on the PC (git-ignored); rule.json is committed.
"""
import csv
import glob
import itertools
import json
import math
import os
import sys

GRAVITY = 9.81
SMOOTH = 5  # samples, ~100 ms at 50 Hz
MIN_GAP_MS = 300.0
TROUGH_MS = 400.0
TRIAL_MS = 20_000.0

GRID = {"lo": [0.6, 0.8, 1.0, 1.2], "hi": [4, 6, 8, 10], "gmax": [2, 3, 4, 5]}
DEV_WALKS = ["dev-walk-2", "dev-walk-3", "dev-walk-4"]  # dev-walk-1 excluded (tester shook at the start)
DEV_SHAKES = ["dev-shake-1", "dev-shake-2", "dev-shake-3"]
RULE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rule.json")


def load(path):
    """Events in time order: ('a'|'g', t_ms, x, y, z)."""
    rows = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r["type"] in ("a", "g"):
                rows.append((r["type"], float(r["t_ms"]), float(r["x"]), float(r["y"]), float(r["z"])))
    rows.sort(key=lambda r: r[1])
    return rows


class Detector:
    """Online and causal, exactly as AccelStepDetector.kt: feed events in time order, read `steps`."""

    def __init__(self, lo, hi, gmax):
        self.lo, self.hi, self.gmax = lo, hi, gmax
        self.raw = []  # last SMOOTH magnitudes
        self.hist = []  # (t, smoothed) within TROUGH_MS + a margin
        self.gyro = 0.0
        self.last_step = -1e18
        self.steps = 0

    def gyroscope(self, t, x, y, z):
        self.gyro = math.sqrt(x * x + y * y + z * z)

    def accelerometer(self, t, x, y, z):
        self.raw.append(math.sqrt(x * x + y * y + z * z))
        if len(self.raw) > SMOOTH:
            self.raw.pop(0)
        s = sum(self.raw) / len(self.raw)
        self.hist.append((t, s))
        while self.hist and self.hist[0][0] < t - TROUGH_MS - 100:
            self.hist.pop(0)
        if len(self.hist) < 3:
            return
        (_, s2), (t1, s1), (_, s0) = self.hist[-3], self.hist[-2], self.hist[-1]
        if not (s1 > s2 and s1 >= s0):  # hist[-2] is a local maximum
            return
        if s1 <= GRAVITY or t1 - self.last_step < MIN_GAP_MS:
            return
        trough = min(v for (tt, v) in self.hist[:-1] if tt >= t1 - TROUGH_MS)
        rise = s1 - trough
        if self.lo <= rise <= self.hi and self.gyro <= self.gmax:
            self.steps += 1
            self.last_step = t1


def count(rows, lo, hi, gmax, until_ms=TRIAL_MS):
    d = Detector(lo, hi, gmax)
    for kind, t, x, y, z in rows:
        if t > until_ms:
            break
        (d.accelerometer if kind == "a" else d.gyroscope)(t, x, y, z)
    return d.steps


def pick(data):
    files = {n: load(os.path.join(data, n + ".csv")) for n in DEV_WALKS + DEV_SHAKES}
    table = []
    for lo, hi, gmax in itertools.product(GRID["lo"], GRID["hi"], GRID["gmax"]):
        walks = [count(files[n], lo, hi, gmax) for n in DEV_WALKS]
        shakes = [count(files[n], lo, hi, gmax) for n in DEV_SHAKES]
        table.append({"lo": lo, "hi": hi, "gmax": gmax, "walks": walks, "shakes": shakes})
    ok = [r for r in table if min(r["walks"]) >= 30]
    print(f"{len(ok)} of {len(table)} settings give every dev walk >= 30 steps")
    for r in sorted(ok, key=lambda r: (max(r["shakes"]), -min(r["walks"]), r["hi"], r["gmax"]))[:10]:
        print(r)
    if not ok:
        print("no setting passes the dev bar: Part B is NO-GO on dev")
        return
    best = min(ok, key=lambda r: (max(r["shakes"]), -min(r["walks"]), r["hi"], r["gmax"]))
    rule = {k: best[k] for k in ("lo", "hi", "gmax")}
    rule.update({"smoothSamples": SMOOTH, "minGapMs": MIN_GAP_MS, "troughMs": TROUGH_MS, "dev": best})
    with open(RULE, "w") as f:
        json.dump(rule, f, indent=2)
        f.write("\n")
    print("picked", rule)


def score(data, prefix):
    rule = json.load(open(RULE))
    for path in sorted(glob.glob(os.path.join(data, prefix + "*.csv"))):
        n = count(load(path), rule["lo"], rule["hi"], rule["gmax"])
        print(f"{os.path.basename(path):22} {n:3d} steps in 20 s")


def counts(data):
    rule = json.load(open(RULE))
    out = {os.path.basename(p): count(load(p), rule["lo"], rule["hi"], rule["gmax"]) for p in sorted(glob.glob(os.path.join(data, "*.csv")))}
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "pick" and len(sys.argv) == 3:
        pick(sys.argv[2])
    elif cmd == "score" and len(sys.argv) == 4:
        score(sys.argv[2], sys.argv[3])
    elif cmd == "counts" and len(sys.argv) == 3:
        counts(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)

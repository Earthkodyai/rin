"""Score S5 part B: the tester's own objects, matched by image embeddings, behind a light gate.

  python score_b.py trials.jsonl b-dev                         sweep + pick (writes picked-b.json)
  python score_b.py trials.jsonl b-heldout --config frozen-b.json   score the frozen rule only

Everything below was committed before any part B data existed.

Setup (once, like onboarding in the app): a 6 s teach scan per object -> gallery of embeddings;
a 15 s from-bed scan with the lights on -> bed gallery. Latest non-discarded scan wins.

App rule, per object o and model m (MobileNetV3 small or large, cosine on L2-normalised vectors):
  sim(frame, o)  = max cosine between the frame and o's teach gallery
  bed_max(o)     = max over bed-scan frames of sim(bed frame, o)
  thr(o)         = bed_max(o) + margin
  light gate     = a frame counts only when the median luma of the last 5 frames >= 70
                   (70 was chosen from part A's dev luma: 55/58 lit trials vs 6/58 wake trials pass)
  found          = k consecutive counted frames with sim >= thr(o), within the 8 s window
One (model, margin, k) is used for every object, because the app can't tune per object without
trials. Dev pick: the setting where the most objects reach >= 90% with ZERO from-bed triggers
(any object, any negative trial); ties -> larger margin, then smaller k, then the small model.
Pass bar (held-out, frozen setting): >= 5 objects at >= 90% and no from-bed trigger -> GO.
"""
import base64
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

MARGINS = [round(0.01 * i, 2) for i in range(0, 21)]
KS = [1, 2, 3]
MODELS = {"small": "es", "large": "el"}
GATE_LUMA = 70
GATE_FRAMES = 5
PASS = 0.9


def load(path):
    recs, discarded = {}, set()
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        o = json.loads(line)
        if o["type"] in ("trial", "teach", "bedscan"):
            recs[o["id"]] = o
        elif o["type"] == "discard":
            discarded.add(o["id"])
    return [r for i, r in recs.items() if i not in discarded]


def emb(frame, key):
    v = np.frombuffer(base64.b64decode(frame[key]), dtype="<f2").astype(np.float32)
    return v / (np.linalg.norm(v) or 1.0)


def matrix(frames, key):
    return np.stack([emb(f, key) for f in frames if key in f]) if frames else np.zeros((0, 1))


def gate(frames):
    out, lumas = [], []
    for f in frames:
        lumas.append(f["luma"])
        out.append(statistics.median(lumas[-GATE_FRAMES:]) >= GATE_LUMA)
    return out


def found_at(sims, gates, times, thr, k):
    run = 0
    for s, g, t in zip(sims, gates, times):
        run = run + 1 if (g and s >= thr) else 0
        if run >= k:
            return t
    return None


def wilson(h, n, z=1.96):
    if n == 0:
        return (0, 0)
    p = h / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return (max(0, c - m), min(1, c + m))


def pct(xs, q):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(q * (len(xs) - 1))))] if xs else None


class Prepared:
    """Per model: similarity of every trial frame to every object's gallery, computed once."""

    def __init__(self, recs, session, key):
        latest = {}
        for r in sorted((r for r in recs if r["type"] in ("teach", "bedscan")), key=lambda r: r["start"]):
            latest[(r["type"], r["scene"])] = r
        self.galleries = {s: matrix(r["frames"], key) for (t, s), r in latest.items() if t == "teach"}
        bed = latest.get(("bedscan", "bed-scan"))
        if bed is None:
            sys.exit("no bed scan recorded")
        bed_m = matrix(bed["frames"], key)
        self.bed_max = {o: float((bed_m @ g.T).max()) for o, g in self.galleries.items()}
        self.trials = [r for r in recs if r["type"] == "trial" and r["session"] == session]
        self.sims = {}
        for t in self.trials:
            fm = matrix(t["frames"], key)
            self.sims[t["id"]] = {o: ((fm @ g.T).max(axis=1) if len(fm) else np.zeros(0)) for o, g in self.galleries.items()}
            t["_gate"] = gate(t["frames"])
            t["_times"] = [f["t"] for f in t["frames"]]

    def detect(self, trial, obj, margin, k):
        return found_at(self.sims[trial["id"]][obj], trial["_gate"], trial["_times"], self.bed_max[obj] + margin, k)

    def evaluate(self, margin, k):
        negs = [t for t in self.trials if t["kind"] == "negative"]
        per = {}
        for o in self.galleries:
            ts = [t for t in self.trials if t["scene"] == o]
            if not ts:
                continue
            times = [self.detect(t, o, margin, k) for t in ts]
            hits = [x for x in times if x is not None]
            false = [n["scene"] for n in negs if self.detect(n, o, margin, k) is not None]
            per[o] = {"hits": len(hits), "n": len(ts), "times": hits, "false": false}
        any_false = any(v["false"] for v in per.values())
        passing = [o for o, v in per.items() if v["hits"] / v["n"] >= PASS and not v["false"]]
        return per, any_false, passing


def report(name, prep, margin, k):
    per, any_false, passing = prep.evaluate(margin, k)
    print(f"\n## {name}: margin {margin:.2f}, k {k}")
    for o, v in sorted(per.items()):
        lo, hi = wilson(v["hits"], v["n"])
        print(f"  {o}: {v['hits']}/{v['n']} = {v['hits'] / v['n']:.0%} (95% CI {lo:.0%}-{hi:.0%}), "
              f"thr {prep.bed_max[o] + margin:.3f} (bed max {prep.bed_max[o]:.3f}), "
              f"time to find p50/p95 {pct(v['times'], .5)}/{pct(v['times'], .95)} ms, "
              f"from-bed triggers: {v['false'] or 'none'}{'  PASS' if o in passing else ''}")
    # Showing a different taught object: the mission is still out of bed, so this is reported, not scored.
    conf = []
    for t in prep.trials:
        if t["kind"] != "target":
            continue
        for o in prep.galleries:
            if o != t["scene"] and prep.detect(t, o, margin, k) is not None:
                conf.append(f"{t['scene']}->{o}")
    counts = defaultdict(int)
    for c in conf:
        counts[c] += 1
    print("  cross-object matches: " + (", ".join(f"{c} x{n}" for c, n in sorted(counts.items())) or "none"))
    verdict = "GO" if len(passing) >= 5 else "not yet"
    print(f"  => {len(passing)} objects pass: {', '.join(passing) or '-'} -> {verdict} (need 5)")
    return passing


def main():
    args = sys.argv[1:]
    cfg = None
    if "--config" in args:
        i = args.index("--config")
        cfg = json.loads(Path(args[i + 1]).read_text(encoding="utf-8"))
        del args[i:i + 2]
    recs = load(args[0])
    session = args[1]
    trials = [r for r in recs if r["type"] == "trial" and r["session"] == session]
    negs = [t for t in trials if t["kind"] == "negative"]
    print(f"# S5 part B, {session}: {len(trials)} trials ({len(negs)} from bed)")
    frames = [f for t in trials for f in t["frames"]]
    if frames:
        print(f"labeler p50 {pct([f['inf'] for f in frames], .5)} ms, both embedders p50/p95 "
              f"{pct([f.get('emb_ms', 0) for f in frames], .5)}/{pct([f.get('emb_ms', 0) for f in frames], .95)} ms, "
              f"frames per trial median {statistics.median(len(t['frames']) for t in trials)}")

    # Light gate on its own: lit trials should open it, dark ones must not.
    lit = [t for t in trials if t["light"] == "on"]
    dark = [t for t in trials if t["light"] == "dark"]
    open_lit = [t for t in lit if any(gate(t["frames"]))]
    open_dark = [t for t in dark if any(gate(t["frames"]))]
    print(f"light gate (luma >= {GATE_LUMA}): opens in {len(open_lit)}/{len(lit)} lit trials, "
          f"{len(open_dark)}/{len(dark)} dark trials")

    preps = {m: Prepared(recs, session, key) for m, key in MODELS.items()}
    for m, p in preps.items():
        print(f"{m}: taught {len(p.galleries)} objects; bed max per object: " +
              ", ".join(f"{o} {v:.3f}" for o, v in sorted(p.bed_max.items())))

    if cfg:
        report(f"FROZEN {cfg['model']}", preps[cfg["model"]], cfg["margin"], cfg["k"])
        other = "large" if cfg["model"] == "small" else "small"
        report(f"(for comparison, not the verdict) {other}", preps[other], cfg["margin"], cfg["k"])
        return

    best = None
    for m, p in preps.items():
        print(f"\n## sweep {m}: objects passing with zero from-bed triggers (rows margin, cols k={KS})")
        for margin in MARGINS:
            cells = []
            for k in KS:
                per, any_false, passing = p.evaluate(margin, k)
                cells.append("  x " if any_false else f"{len(passing):3d} ")
                if not any_false:
                    key = (len(passing), margin, -k, m == "small")
                    if best is None or key > best[0]:
                        best = (key, {"model": m, "margin": margin, "k": k})
            print(f"  {margin:.2f}: " + " ".join(cells))
    if best is None:
        print("\nno setting avoids from-bed triggers")
        return
    pick = best[1]
    report(f"PICKED {pick['model']}", preps[pick["model"]], pick["margin"], pick["k"])
    out = Path(args[0]).with_name("picked-b.json")
    out.write_text(json.dumps(pick, indent=1), encoding="utf-8")
    print(f"\npicked -> {out.name}; copy to tools/frozen-b.json and commit BEFORE the held-out session")


if __name__ == "__main__":
    main()

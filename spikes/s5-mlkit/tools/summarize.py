"""Score S5 trial logs. Labels + timing only; the app never saves images.

  python summarize.py trials.jsonl [session] [--config frozen.json]

Without --config (dev): for every target, sweep threshold x consecutive frames, report hit rate
and false triggers on the from-bed negatives, show which labels actually appear, and pick a
spec with the pre-registered rule. With --config (held-out): score the frozen specs only.

Pre-registered rule (per target, dev session): among (threshold, k) with ZERO triggers on the
dev negatives, take the highest hit rate; ties -> higher threshold, then smaller k.
A target passes at >= 90% hits with no trigger on the negatives. GO needs 5 passing targets.
"""
import json
import math
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

THRESHOLDS = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
KS = [1, 2, 3]
PASS = 0.9


def load(path):
    trials, discarded = {}, set()
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        o = json.loads(line)
        if o["type"] == "trial":
            trials[o["id"]] = o
        elif o["type"] == "discard":
            discarded.add(o["id"])
    return [t for i, t in trials.items() if i not in discarded], len(discarded)


def conf(frame, labels):
    return max((c for name, c in frame["labels"] if name in labels), default=0.0)


def detect(trial, labels, thr, k):
    """ms from Start to the k-th consecutive frame at/above thr, or None."""
    run = 0
    for f in trial["frames"]:
        run = run + 1 if conf(f, labels) >= thr else 0
        if run >= k:
            return f["t"]
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


def score(trials, negs, spec):
    labels, thr, k = set(spec["labels"]), spec["thr"], spec["k"]
    times = [detect(t, labels, thr, k) for t in trials]
    by_light = {}
    for light in ("wake", "on"):
        pairs = [(t, x) for t, x in zip(trials, times) if t["light"] == light]
        by_light[light] = (sum(1 for _, x in pairs if x is not None), len(pairs))
    false = [n["scene"] for n in negs if detect(n, labels, thr, k) is not None]
    hits = [x for x in times if x is not None]
    return {"hits": len(hits), "n": len(trials), "times": hits, "by_light": by_light, "false": false}


def label_census(trials, min_conf=0.5):
    """(label, share of trials where it reached min_conf, median of its per-trial max)."""
    seen, maxes = Counter(), defaultdict(list)
    for t in trials:
        best = defaultdict(float)
        for f in t["frames"]:
            for name, c in f["labels"]:
                best[name] = max(best[name], c)
        for name, c in best.items():
            maxes[name].append(c)
            if c >= min_conf:
                seen[name] += 1
    rows = [(n, seen[n] / len(trials), statistics.median(maxes[n])) for n in maxes]
    return sorted(rows, key=lambda r: (-r[1], -r[2]))


def pick(trials, negs, labels):
    best = None
    for thr in THRESHOLDS:
        for k in KS:
            spec = {"labels": labels, "thr": thr, "k": k}
            s = score(trials, negs, spec)
            if s["false"]:
                continue
            key = (s["hits"], thr, -k)
            if best is None or key > best[0]:
                best = (key, spec, s)
    return best


def main():
    args = sys.argv[1:]
    cfg_path = None
    if "--config" in args:
        i = args.index("--config")
        cfg_path = args[i + 1]
        del args[i:i + 2]
    trials, n_disc = load(args[0])
    session = args[1] if len(args) > 1 else ("heldout" if cfg_path else "dev")
    trials = [t for t in trials if t["session"] == session]
    negs = [t for t in trials if t["kind"] == "negative"]
    by_scene = defaultdict(list)
    for t in trials:
        if t["kind"] == "target":
            by_scene[t["scene"]].append(t)

    print(f"# S5 {session}: {len(trials)} trials ({len(negs)} negative); {n_disc} discarded in the whole log")
    frames = [f for t in trials for f in t["frames"]]
    if frames:
        infs = [f["inf"] for f in frames]
        per = [len(t["frames"]) * 1000 / t["window_ms"] for t in trials]
        print(f"labeler latency p50/p95 {pct(infs, .5)}/{pct(infs, .95)} ms; analysed fps median {statistics.median(per):.1f}")
    for light in ("wake", "on"):
        ts = [t for t in trials if t["light"] == light and t["frames"]]
        if ts:
            lux = [t["lux_start"] for t in ts if t["lux_start"] >= 0]
            luma = [statistics.median(f["luma"] for f in t["frames"]) for t in ts]
            print(f"light={light}: {len(ts)} trials, lux median {statistics.median(lux) if lux else 'n/a'}, "
                  f"frame luma median {statistics.median(luma)}")

    base = json.loads((Path(__file__).parent / "labels.json").read_text(encoding="utf-8"))["targets"]
    frozen = json.loads(Path(cfg_path).read_text(encoding="utf-8"))["targets"] if cfg_path else None

    if negs:
        print("\n## Negatives (from bed): labels that reach 0.5")
        for name, share, med in label_census(negs)[:12]:
            if share > 0:
                print(f"  {name}: {share:.0%} of trials (median max {med:.2f})")

    passed, picked = [], {}
    for scene, ts in sorted(by_scene.items()):
        print(f"\n## {scene} ({len(ts)} trials)")
        print("  labels seen (share of trials >= 0.5 / median max): " +
              ", ".join(f"{n} {s:.0%}/{m:.2f}" for n, s, m in label_census(ts)[:8]))
        if frozen is not None:
            if scene not in frozen:
                print("  not in the frozen config: skipped")
                continue
            spec, how = frozen[scene], "frozen"
            s = score(ts, negs, spec)
        else:
            labels = base.get(scene)
            if not labels:
                print("  no candidate labels (custom target): choose from the census above")
                continue
            for thr in (0.5, 0.7):
                row = "  ".join(f"k{k} {score(ts, negs, {'labels': labels, 'thr': thr, 'k': k})['hits']}/{len(ts)}" for k in KS)
                print(f"  thr {thr}: {row}")
            b = pick(ts, negs, labels)
            if b is None:
                print("  every spec triggers on a from-bed negative")
                continue
            _, spec, s = b
            how = "picked"
            picked[scene] = spec
        lo, hi = wilson(s["hits"], s["n"])
        rate = s["hits"] / s["n"]
        ok = rate >= PASS and not s["false"]
        if ok:
            passed.append(scene)
        w, o = s["by_light"]["wake"], s["by_light"]["on"]
        print(f"  {how} thr {spec['thr']:.1f} k {spec['k']} {sorted(spec['labels'])}: {s['hits']}/{s['n']} = {rate:.0%} "
              f"(95% CI {lo:.0%}-{hi:.0%}), wake {w[0]}/{w[1]}, lights on {o[0]}/{o[1]}, "
              f"time to find p50/p95 {pct(s['times'], .5)}/{pct(s['times'], .95)} ms, "
              f"negatives triggered: {s['false'] or 'none'}{'  PASS' if ok else ''}")

    verdict = "GO" if len(passed) >= 5 else "not yet"
    print(f"\n# {len(passed)} targets pass (>= {PASS:.0%}, no from-bed trigger): {', '.join(passed) or '-'} -> {verdict} (need 5)")
    if picked:
        out = Path(args[0]).with_name(f"picked-{session}.json")
        out.write_text(json.dumps({"targets": picked}, indent=1), encoding="utf-8")
        print(f"picked specs -> {out.name}; copy to tools/frozen.json and commit BEFORE the held-out morning")


if __name__ == "__main__":
    main()

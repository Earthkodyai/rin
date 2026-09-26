"""Summarize S3 replay results: intent accuracy (overall, per condition, per intent) and latency per engine.

Usage: python summarize.py results/<set>-<stamp>.jsonl [--misses]
Latency = time the final transcript arrived minus the moment speech ended in the fed audio,
so it includes each engine's own end-of-speech wait.
"""
import json
import sys
from collections import defaultdict


def pct(xs, p):
    xs = sorted(xs)
    if not xs:
        return float("nan")
    k = (len(xs) - 1) * p / 100
    lo, hi = int(k), min(int(k) + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)


def main(path, show_misses):
    rows = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    for r in rows:
        if r["phase"] in ("header", "support", "vosk-model", "engine-error"):
            print({k: v for k, v in r.items() if k != "phase"} | {"phase": r["phase"]})
    utt = [r for r in rows if r["phase"] == "utt"]
    by_engine = defaultdict(list)
    for r in utt:
        by_engine[r["engine"]].append(r)

    conds = sorted({r["cond"] for r in utt})
    intents = sorted({r["intent"] for r in utt})
    print()
    print("| Engine | Intent accuracy | " + " | ".join(conds) + " | Latency p50 / p95 (ms) | Errors |")
    print("|---|---|" + "---|" * len(conds) + "---|---|")
    for eng, rs in by_engine.items():
        ok = sum(r["ok"] for r in rs)
        per_cond = []
        for c in conds:
            sub = [r for r in rs if r["cond"] == c]
            per_cond.append(f"{sum(r['ok'] for r in sub)}/{len(sub)}" if sub else "-")
        lat = [r["latencyMs"] for r in rs if not r["err"] and r["text"]]
        errs = defaultdict(int)
        for r in rs:
            if r["err"]:
                errs[r["err"]] += 1
        err_s = ", ".join(f"{k} {v}" for k, v in errs.items()) or "0"
        print(f"| {eng} | **{ok}/{len(rs)} = {100 * ok / len(rs):.1f}%** | " + " | ".join(per_cond)
              + f" | {pct(lat, 50):.0f} / {pct(lat, 95):.0f} | {err_s} |")

    print()
    print("| Engine | " + " | ".join(i.lower() for i in intents) + " |")
    print("|---|" + "---|" * len(intents))
    for eng, rs in by_engine.items():
        cells = []
        for i in intents:
            sub = [r for r in rs if r["intent"] == i]
            cells.append(f"{sum(r['ok'] for r in sub)}/{len(sub)}")
        print(f"| {eng} | " + " | ".join(cells) + " |")

    # Same clip, different engines: shows whether a miss is the STT or the matcher.
    if show_misses:
        print()
        ids = sorted({r["id"] for r in utt})
        engines = list(by_engine)
        for i in ids:
            per = {r["engine"]: r for r in utt if r["id"] == i}
            if all(per[e]["ok"] for e in engines if e in per):
                continue
            first = next(iter(per.values()))
            print(f"{i} [{first['intent']}, {first['cond']}]")
            for e in engines:
                if e in per:
                    r = per[e]
                    mark = "ok " if r["ok"] else "XX "
                    print(f"   {mark}{e:16s} {r['pred']:8s} \"{r['text']}\" {r['err']}")


if __name__ == "__main__":
    main(sys.argv[1], "--misses" in sys.argv)

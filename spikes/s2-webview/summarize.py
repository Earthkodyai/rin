"""Summarise results/runs.log (written by s2.sh run) into one row per run."""
import json
import re
import sys
from pathlib import Path

log = Path(__file__).parent / "results" / "runs.log"
runs, cur = [], None
for line in log.read_text(encoding="utf8").splitlines():
    if line.startswith("=== "):
        m = re.match(r"=== \S+ \S+ (\S+) .*battTemp=(\d+)->(\d+) thermal=(\S*)", line)
        cur = {"label": m.group(1), "temp": f"{int(m.group(2))/10:.1f}->{int(m.group(3))/10:.1f}", "thermal": m.group(4)}
        runs.append(cur)
    elif line.startswith("{"):
        d = json.loads(line)
        if d.get("phase") == "loaded":
            cur.update(pr=d["pixelRatio"], canvas="x".join(map(str, d["canvas"])), load=d["ms"]["nativeToFirstFrame"],
                       compile=d["ms"]["compileAndFirstFrame"], parse=d["ms"]["parse"])
        elif d.get("phase") == "fps":
            cur.update(sec=d["seconds"], fps=d["avgFps"], low1=d["low1Fps"], p99=d["frameMs"]["p99"], j33=d["over33ms"], heap=d["heapMB"])
        elif d.get("phase") == "native":
            cur["wv"] = d["webViewCreateMs"]
        elif "error" in d:
            cur["error"] = d["error"][:80]
    elif line.startswith("meminfo app:"):
        g = re.search(r"Graphics: (\d+)", line); t = re.search(r"TOTAL PSS: (\d+)", line)
        cur.update(appPssMB=round(int(t.group(1)) / 1024) if t else None, gfxMB=round(int(g.group(1)) / 1024) if g else None)
    elif line.startswith("meminfo renderer"):
        cur["rendMB"] = sum(round(int(v) / 1024) for v in re.findall(r":(\d+)", line))

cols = ["label", "pr", "canvas", "load", "wv", "parse", "compile", "sec", "fps", "low1", "p99", "j33", "appPssMB", "gfxMB", "rendMB", "heap", "temp", "thermal", "error"]
rows = [r for r in runs if len(sys.argv) < 2 or re.search(sys.argv[1], r["label"])]
print("| " + " | ".join(cols) + " |")
print("|" + "---|" * len(cols))
for r in rows:
    print("| " + " | ".join(str(r.get(c, "")) for c in cols) + " |")

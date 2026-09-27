"""Write <log>.gz without the image embeddings: the copy that is committed.

Embeddings describe the tester's home, so they stay in the git-ignored raw log on the PC.
Labels, brightness and timing are kept, so part A can be re-scored from the public copy.
"""
import gzip
import json
import sys

src = sys.argv[1]
with open(src, encoding="utf-8") as f, gzip.GzipFile(src + ".gz", "wb", compresslevel=9, mtime=0) as raw:
    for line in f:
        o = json.loads(line)
        for fr in o.get("frames", []):
            fr.pop("es", None)
            fr.pop("el", None)
        raw.write((json.dumps(o, separators=(",", ":")) + "\n").encode("utf-8"))

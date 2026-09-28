"""Scores the QR size-bar dev trials (docs/spikes/3.2-qr.md, Part A) and picks the bar with the frozen rule.

Pull the CSVs first (app-private, sizes only):
  adb exec-out run-as io.github.earthkodyai.rinalarm tar c -C files qrlab | tar x -C tools/qrlab/data

Usage: python tools/qrlab/score.py tools/qrlab/data/qrlab
Trials are named dev-close-N and dev-bed-N. Standard library only.
"""

import csv
import math
import statistics
import sys
from pathlib import Path

MARGIN = 1.5
LOW, HIGH = 0.10, 0.30


def sizes(path: Path) -> tuple[list[float], int, str]:
    """Sticker sizes over the frames that read it, the frame count, and whether the light came on."""
    read, frames, torch = [], 0, "off"
    with path.open(newline="") as f:
        for row in csv.DictReader(f):
            frames += 1
            if row["sticker_fraction"]:
                read.append(float(row["sticker_fraction"]))
            if row["torch"] != "off":
                torch = row["torch"]
    return read, frames, torch


def pick_bar(close: list[float], bed: list[float]) -> tuple[float, bool]:
    """bar = min(close) / 1.5, floored to 0.01, clamped to [0.10, 0.30]; valid only if bar >= 1.5 x max(bed)."""
    bar = math.floor(min(close) / MARGIN * 100) / 100
    bar = min(max(bar, LOW), HIGH)
    return bar, bar >= MARGIN * max(bed, default=0.0)


def main(folder: str) -> None:
    close, bed = [], []
    print(f"{'trial':<14}{'frames':>7}{'read':>6}{'median':>8}{'max':>7}  light")
    for path in sorted(Path(folder).glob("dev-*.csv")):
        read, frames, torch = sizes(path)
        median = statistics.median(read) if read else 0.0
        top = max(read, default=0.0)
        print(f"{path.stem:<14}{frames:>7}{len(read):>6}{median:>8.3f}{top:>7.3f}  {torch}")
        if path.stem.startswith("dev-close"):
            close.append(median)
        elif path.stem.startswith("dev-bed"):
            bed.append(top)
    if not close:
        sys.exit("no dev-close trials")
    bar, valid = pick_bar(close, bed)
    print(f"\nclose min {min(close):.3f}, bed max {max(bed, default=0.0):.3f}")
    print(f"bar {bar:.2f} -> {'VALID' if valid else 'INVALID: move the sticker (protocol step 3)'}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "tools/qrlab/data/qrlab")

"""Synthesizes the lip-sync eval words (vowels.json) with Kokoro-82M (Apache-2.0), locally.

Run with the S4 spike's venv, which has kokoro-onnx and the model:
  ../../../spikes/s4-voice/.venv/Scripts/python make_vowels.py
Writes clips/<voice>/<shape>-<word>.wav (git-ignored; rebuildable).
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro_onnx import Kokoro

DIR = Path(__file__).parent
SPIKE = DIR.parents[2] / "spikes" / "s4-voice" / "models"
spec = json.loads((DIR / "vowels.json").read_text(encoding="utf-8"))
k = Kokoro(str(SPIKE / "kokoro-v1.0.int8.onnx"), str(SPIKE / "voices-v1.0.bin"))
for split in ("dev", "test"):
    for voice in spec[split]["voices"]:
        out = DIR / "clips" / voice
        out.mkdir(parents=True, exist_ok=True)
        for shape, words in spec[split]["words"].items():
            for word in words:
                f = out / f"{shape}-{word}.wav"
                if f.exists():
                    continue
                audio, sr = k.create(word + ".", voice=voice, speed=0.9, lang="en-us")
                sf.write(f, np.asarray(audio), sr)
        print(voice, "done", flush=True)

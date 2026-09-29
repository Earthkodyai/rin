"""Synthetic voices reading the held-out protocol (task 3.5): a baseline that is not the tester's voice.

Run with the S4 venv (kokoro-onnx), from the repo root:
  spikes/s4-voice/.venv/Scripts/python tools/stt/make-synth.py
Writes tools/stt/recordings/synth/<voice>-<item>.wav (16 kHz mono, git-ignored) and docs/spikes/3.5/synth.json (the
protocol: the held-out items minus the mumbles, which a TTS cannot do, once per voice). Then:
  python tools/stt/pc_replay.py docs/spikes/3.5/synth.json tools/stt/recordings/synth docs/spikes/3.5/synth-frozen.jsonl

The voice set was fixed before any clip was made (docs/spikes/3.5-repeat-after-rin.md). Kokoro turns text into
English phonemes before speaking, so its non-English voices change the timbre, not the accent.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro_onnx import Kokoro

ROOT = Path(__file__).resolve().parents[2]
S4 = ROOT / "spikes/s4-voice/models"
OUT = ROOT / "tools/stt/recordings/synth"
PROTOCOL = ROOT / "docs/spikes/3.5/synth.json"
POOL = {s["id"]: s["text"] for s in json.loads((ROOT / "app/src/main/assets/repeat/sentences.json").read_text(encoding="utf-8"))["sentences"]}

# (name, engine, voice, lang, speed, group)
VOICES = [
    ("af_heart", "kokoro", "af_heart", "en-us", 1.0, "native"),
    ("af_bella", "kokoro", "af_bella", "en-us", 1.0, "native"),
    ("am_michael", "kokoro", "am_michael", "en-us", 1.0, "native"),
    ("am_adam", "kokoro", "am_adam", "en-us", 1.0, "native"),
    ("bf_emma", "kokoro", "bf_emma", "en-gb", 1.0, "native"),
    ("bm_george", "kokoro", "bm_george", "en-gb", 1.0, "native"),
    ("ef_dora", "kokoro", "ef_dora", "en-us", 1.0, "other-timbre"),
    ("hf_alpha", "kokoro", "hf_alpha", "en-us", 1.0, "other-timbre"),
    ("jf_alpha", "kokoro", "jf_alpha", "en-us", 1.0, "other-timbre"),
    ("zf_xiaobei", "kokoro", "zf_xiaobei", "en-us", 1.0, "other-timbre"),
    ("zm_yunxi", "kokoro", "zm_yunxi", "en-us", 1.0, "other-timbre"),
    ("pm_alex", "kokoro", "pm_alex", "en-us", 1.0, "other-timbre"),
    ("af_heart-slow", "kokoro", "af_heart", "en-us", 0.75, "slow"),
    ("am_michael-slow", "kokoro", "am_michael", "en-us", 0.75, "slow"),
    ("sapi_david", "sapi", "Microsoft David Desktop", "en-US", 1.0, "windows"),
    ("sapi_zira", "sapi", "Microsoft Zira Desktop", "en-US", 1.0, "windows"),
]


def to_16k(audio, rate):
    """Band-limited resample to 16 kHz (upsample, windowed-sinc low-pass, decimate); numpy only."""
    audio = np.asarray(audio, dtype=np.float64)
    if rate == 16_000:
        return audio
    from math import gcd

    g = gcd(rate, 16_000)
    up, down = 16_000 // g, rate // g
    x = np.zeros(len(audio) * up)
    x[::up] = audio * up
    cutoff = 0.5 / max(up, down)
    n = np.arange(-64 * max(up, down), 64 * max(up, down) + 1)
    h = 2 * cutoff * np.sinc(2 * cutoff * n) * np.hanning(len(n))
    return np.convolve(x, h, mode="same")[::down]


def pad(audio):
    """A second of quiet before and after, like a real try (the mic opens before the user speaks)."""
    z = np.zeros(16_000)
    return np.concatenate([z, audio, z])


def main():
    items = [i for i in json.loads((ROOT / "app/src/debug/assets/repeat-lab/heldout.json").read_text(encoding="utf-8"))["items"] if i["kind"] != "mumble"]
    OUT.mkdir(parents=True, exist_ok=True)
    kokoro = Kokoro(str(S4 / "kokoro-v1.0.int8.onnx"), str(S4 / "voices-v1.0.bin"))
    protocol, sapi_jobs = [], []
    for name, engine, voice, lang, speed, group in VOICES:
        for it in items:
            text = it["read"] or POOL[it["target"]]
            wav = OUT / f"{name}-{it['id']}.wav"
            protocol.append({"id": wav.stem, "kind": it["kind"], "target": it["target"], "cond": group, "voice": name, "read": text})
            if wav.exists():
                continue
            if engine == "kokoro":
                audio, rate = kokoro.create(text, voice=voice, speed=speed, lang=lang)
                sf.write(wav, np.clip(pad(to_16k(audio, rate)), -1, 1), 16_000, subtype="PCM_16")
            else:
                sapi_jobs.append({"voice": voice, "text": text, "path": str(wav)})
        print(name, "done", flush=True)
    if sapi_jobs:
        jobs = OUT / "sapi-jobs.json"
        jobs.write_text(json.dumps(sapi_jobs), encoding="utf-8")
        script = (
            "Add-Type -AssemblyName System.Speech;"
            f"$jobs = Get-Content -Raw -Encoding UTF8 '{jobs}' | ConvertFrom-Json;"
            "$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono);"
            "foreach ($j in $jobs) { $s = New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.SelectVoice($j.voice);"
            " $s.SetOutputToWaveFile($j.path, $fmt); $s.Speak($j.text); $s.Dispose() }"
        )
        subprocess.run(["powershell", "-NoProfile", "-Command", script], check=True)
        jobs.unlink()
        # SAPI writes no lead-in; add the same second of quiet on both sides.
        for j in sapi_jobs:
            audio, rate = sf.read(j["path"])
            sf.write(j["path"], pad(audio), 16_000, subtype="PCM_16")
        print("sapi done", flush=True)
    PROTOCOL.parent.mkdir(parents=True, exist_ok=True)
    with open(PROTOCOL, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"_note": "Held-out items minus mumbles, once per synthetic voice (tools/stt/make-synth.py).", "items": protocol}, indent=1) + "\n")
    print(len(protocol), "items ->", PROTOCOL)


if __name__ == "__main__":
    sys.exit(main())

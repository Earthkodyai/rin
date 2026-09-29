"""Synthesizes the Repeat after Rin sentences (task 3.5) in the user's ElevenLabs voice "Rin soft" (free plan).

Free-plan output is not licensed for release, so the clips go only into the debug build's git-ignored assets, like
the S4 dev clips; Phase 4's paid month replaces them. Reuses the S4 spike's key and saved voice id:
  python tools/voice/make-repeat-clips.py [--force]
Reads app/src/main/assets/repeat/sentences.json, writes app/src/debug/assets/voice/dev/repeat/<id>.mp3 and skips
clips that exist. Then: node tools/voice/make-mouth.mjs app/src/debug/assets/voice/dev/repeat/*.mp3
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
S4 = ROOT / "spikes" / "s4-voice"
SENTENCES = ROOT / "app" / "src" / "main" / "assets" / "repeat" / "sentences.json"
OUT = ROOT / "app" / "src" / "debug" / "assets" / "voice" / "dev" / "repeat"
# A warm, clear read: the user repeats it word for word.
TAG = "[warmly]"


def key():
    for raw in (S4 / ".env").read_text(encoding="utf-8").splitlines():
        name, _, value = raw.strip().partition("=")
        if name == "ELEVENLABS_API_KEY" and value.strip():
            return value.strip()
    sys.exit(f"no ELEVENLABS_API_KEY in {S4 / '.env'}")


def main():
    force = "--force" in sys.argv
    voice = json.loads((S4 / "out" / "eleven_voices.json").read_text())["el-rin-soft"]
    model = json.loads((S4 / "voices.json").read_text(encoding="utf-8"))["eleven"]["model_tts"]
    api_key = key()
    OUT.mkdir(parents=True, exist_ok=True)
    chars = 0
    for s in json.loads(SENTENCES.read_text(encoding="utf-8"))["sentences"]:
        f = OUT / f"{s['id']}.mp3"
        if f.exists() and not force:
            continue
        text = f"{TAG} {s['text']}"
        req = urllib.request.Request(
            f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_128",
            data=json.dumps({"text": text, "model_id": model}).encode("utf-8"),
            headers={"xi-api-key": api_key, "Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=60) as r:
            f.write_bytes(r.read())
        chars += len(text)
        print(f"{s['id']}: {f.stat().st_size // 1024} KB", flush=True)
    print(f"credits used this run: ~{chars}")


if __name__ == "__main__":
    os.chdir(ROOT)
    main()

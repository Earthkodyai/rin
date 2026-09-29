"""Repeat after Rin on the PC (task 3.5): the game's decoding and judging, mirrored in Python, for voices that are not
the tester's (synthetic now, other people later) without a phone round trip per clip.

  pip install vosk==0.3.45   (the model is S3's: spikes/s3-stt/model/vosk-model-small-en-us-0.15)
  python tools/stt/pc_replay.py <protocol.json> <wav dir> <out.jsonl>

Mirrors, and must stay in step with: RepeatMatcher.grammar/match and MatchRules() (mission/RepeatGame.kt) and
VoskDecoder (mission/AndroidSpeech.kt): 100 ms chunks, words collected across utterances, a try ends 1 s after an
utterance with words when no new speech started, capped at RepeatRules.listenMs (8 s). The Android library is Vosk
0.3.75 and the Python one 0.3.45 (same model), so the phone stays the reference: this script's verdicts are checked
against the phone's replay of the tester's held-out set before any other use (docs/spikes/3.5-repeat-after-rin.md).
"""
import json
import math
import sys
import wave
from pathlib import Path

from vosk import KaldiRecognizer, Model, SetLogLevel

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "spikes/s3-stt/model/vosk-model-small-en-us-0.15"
POOL = {s["id"]: s["text"] for s in json.loads((ROOT / "app/src/main/assets/repeat/sentences.json").read_text(encoding="utf-8"))["sentences"]}
CONTRACTIONS = {"i'm": ["i", "am"], "let's": ["let", "us"], "don't": ["do", "not"]}
MIN_CONF, MIN_COVERAGE = 0.6, 0.6  # MatchRules(), frozen 2026-09-29
RATE, CHUNK, QUIET_SAMPLES, LISTEN_SAMPLES = 16_000, 1_600, 16_000, 8 * 16_000


def words(text):
    return [w.strip(",.!?;:\"").lower().replace("’", "'") for w in text.split(" ") if w.strip(",.!?;:\"")]


def expand(ws):
    return [p for w in ws for p in CONTRACTIONS.get(w, [w])]


def grammar(text):
    ws = words(text)
    long = expand(ws)
    short = [k for k, v in CONTRACTIONS.items() if any(long[i:i + len(v)] == v for i in range(len(long)))]
    return list(dict.fromkeys(ws + long + short)) + ["[unk]"]


def match(text, heard):
    want = expand(words(text))
    got = expand([w["w"] for w in heard if w["c"] >= MIN_CONF])
    lcs = [[0] * (len(got) + 1) for _ in range(len(want) + 1)]
    for i in range(len(want)):
        for j in range(len(got)):
            lcs[i + 1][j + 1] = lcs[i][j] + 1 if want[i] == got[j] else max(lcs[i][j + 1], lcs[i + 1][j])
    needed = max(1, min(len(want), math.ceil(MIN_COVERAGE * len(want) - 1e-6)))
    return lcs[-1][-1], len(want), lcs[-1][-1] >= needed


def decode(model, text, pcm_bytes):
    rec = KaldiRecognizer(model, RATE, json.dumps(grammar(text)))
    rec.SetWords(True)
    heard, unk, samples, quiet_from, done = [], 0, 0, None, False

    def take(result):
        nonlocal unk
        r = json.loads(result).get("result", [])
        unk += sum(1 for x in r if x["word"] == "[unk]")
        got = [{"w": x["word"], "c": round(x["conf"], 3)} for x in r if x["word"] != "[unk]"]
        heard.extend(got)
        return bool(got)

    pcm_bytes = pcm_bytes[: LISTEN_SAMPLES * 2]
    for off in range(0, len(pcm_bytes), CHUNK * 2):
        data = pcm_bytes[off: off + CHUNK * 2]
        samples += len(data) // 2
        if rec.AcceptWaveform(data):
            if take(rec.Result()):
                quiet_from = samples
                if match(text, heard)[2]:
                    done = True
                    break
        elif quiet_from is not None and json.loads(rec.PartialResult()).get("partial", "").replace("[unk]", "").strip():
            quiet_from = None
        if quiet_from is not None and samples - quiet_from >= QUIET_SAMPLES:
            done = True
            break
    if not done:
        take(rec.FinalResult())
    return heard, unk


def read_pcm(path):
    with wave.open(str(path)) as w:
        assert w.getframerate() == RATE and w.getnchannels() == 1 and w.getsampwidth() == 2, f"{path}: need 16 kHz mono 16-bit"
        return w.readframes(w.getnframes())


def main(protocol, wav_dir, out):
    SetLogLevel(-1)
    model = Model(str(MODEL))
    items = json.loads(Path(protocol).read_text(encoding="utf-8"))["items"]
    lines = []
    for it in items:
        wav = Path(wav_dir) / f"{it['id']}.wav"
        if not wav.is_file():
            continue
        heard, unk = decode(model, POOL[it["target"]], read_pcm(wav))
        m, n, ok = match(POOL[it["target"]], heard)
        lines.append(json.dumps({**{k: it[k] for k in ("id", "kind", "target", "cond")}, **({"voice": it["voice"]} if "voice" in it else {}),
                                 "decoys": False, "words": heard, "unk": unk, "matched": m, "of": n, "accepted": ok}))
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{len(lines)} tries -> {out}")


if __name__ == "__main__":
    main(*sys.argv[1:4])

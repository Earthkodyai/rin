"""S4 voice audition: synthesize the same lines with Azure and ElevenLabs voices.

Keys come from spikes/s4-voice/.env (git-ignored), see .env.example. Stdlib only.

  python synth.py azure-voices          check candidate names/styles against the live voice list
  python synth.py azure                 lines x Azure candidates -> out/<key>/<line>.ogg
  python synth.py eleven-design         Voice Design previews -> out/eleven-design/ (pick by ear)
  python synth.py eleven-save KEY GENERATED_VOICE_ID   keep a preview as a voice
  python synth.py eleven                lines x saved ElevenLabs voices -> out/<key>/<line>.mp3
  .venv/Scripts/python synth.py kokoro  lines x local Kokoro voices -> out/<key>/<line>.wav (needs the venv)
  .venv-cb/Scripts/python synth.py chatterbox   lines x Chatterbox candidates -> out/<key>/<line>.wav (CPU, slow)
  python synth.py manifest              out/manifest.json for listen.html
  python synth.py heldout               S3 held-out set read by Azure Thai voices -> ../s3-stt/recordings/azure/
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from xml.sax.saxutils import escape

DIR = Path(__file__).resolve().parent
OUT = DIR / "out"
LINES = json.loads((DIR / "lines.json").read_text(encoding="utf-8"))["lines"]
VOICES = json.loads((DIR / "voices.json").read_text(encoding="utf-8"))
ELEVEN_IDS = OUT / "eleven_voices.json"


def load_env():
    env = DIR / ".env"
    if env.exists():
        for raw in env.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())


def need(name):
    v = os.environ.get(name)
    if not v:
        sys.exit(f"missing {name} (put it in {DIR / '.env'})")
    return v


def http(url, body=None, headers=None, method=None, tries=6):
    """Request with backoff on 429/5xx (Azure F0 allows 20 requests per minute)."""
    for attempt in range(tries):
        req = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < tries - 1:
                wait = float(e.headers.get("Retry-After") or 5 * (attempt + 1))
                print(f"  {e.code}, retry in {wait:.0f}s")
                time.sleep(wait)
                continue
            detail = e.read().decode("utf-8", "replace")[:300]
            raise SystemExit(f"HTTP {e.code} for {url}: {detail}")


# ---------- Azure ----------

def azure_endpoint(path):
    region = need("AZURE_SPEECH_REGION")
    return f"https://{region}.tts.speech.microsoft.com/cognitiveservices/{path}"


def azure_tts(ssml, fmt):
    return http(azure_endpoint("v1"), ssml.encode("utf-8"), {
        "Ocp-Apim-Subscription-Key": need("AZURE_SPEECH_KEY"),
        "Content-Type": "application/ssml+xml",
        "X-Microsoft-OutputFormat": fmt,
        "User-Agent": "rinalarm-s4",
    }, "POST")


def neural_style(voice, emotion):
    supported = VOICES["neural_supported"].get(voice, [])
    style = VOICES["styles"]["neural"][emotion]
    if style in supported:
        return style
    return "friendly" if "friendly" in supported else None


def azure_ssml(v, line):
    text = escape(line["text"])
    emotion = line["emotion"]
    if v["kind"] == "neural":
        style = neural_style(v["name"], emotion)
        if style:
            text = f'<mstts:express-as style="{style}">{text}</mstts:express-as>'
    elif v["kind"] == "hd":
        text = f'[{VOICES["styles"]["hd"][emotion]}] {text}'
    elif v["kind"] == "omni":
        text = f'<mstts:express-as style="{VOICES["styles"]["hd"][emotion]}">{text}</mstts:express-as>'
    return ("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
            "xmlns:mstts='http://www.w3.org/2001/mstts' xml:lang='en-US'>"
            f"<voice name='{v['name']}'>{text}</voice></speak>")


def cmd_azure_voices():
    data = json.loads(http(azure_endpoint("voices/list"),
                           headers={"Ocp-Apim-Subscription-Key": need("AZURE_SPEECH_KEY")}))
    by_name = {d["ShortName"].lower(): d for d in data}
    for v in VOICES["azure"]:
        d = by_name.get(v["name"].lower())
        if d is None:
            print(f"{v['key']:10} {v['name']}: not in this region's list (HD names may be omitted; synth will tell)")
        else:
            print(f"{v['key']:10} {v['name']}: ok, styles={d.get('StyleList', [])}")
    for t in ("th-TH-PremwadeeNeural", "th-TH-NiwatNeural", "th-TH-AcharaNeural"):
        print(f"{'heldout':10} {t}: {'ok' if t.lower() in by_name else 'MISSING'}")


def cmd_azure():
    chars = 0
    for v in VOICES["azure"]:
        d = OUT / v["key"]
        d.mkdir(parents=True, exist_ok=True)
        for line in LINES:
            f = d / f"{line['id']}.ogg"
            if f.exists():
                continue
            f.write_bytes(azure_tts(azure_ssml(v, line), "ogg-24khz-16bit-mono-opus"))
            chars += len(line["text"])
        print(f"{v['key']}: done")
    print(f"billed text chars this run: ~{chars}")


# ---------- ElevenLabs ----------

def eleven(path, payload):
    return http(f"https://api.elevenlabs.io{path}", json.dumps(payload).encode("utf-8"), {
        "xi-api-key": need("ELEVENLABS_API_KEY"),
        "Content-Type": "application/json",
    }, "POST")


def cmd_eleven_design():
    cfg = VOICES["eleven"]
    d = OUT / "eleven-design"
    d.mkdir(parents=True, exist_ok=True)
    sample = " ".join(line["text"] for line in LINES[:3])  # API needs 100-1000 chars
    found = {}
    for p in cfg["prompts"]:
        res = json.loads(eleven("/v1/text-to-voice/design", {
            "voice_description": p["description"], "model_id": cfg["model_design"], "text": sample}))
        for i, prev in enumerate(res["previews"][:cfg["previews_per_prompt"]], 1):
            name = f"{p['key']}-{i}"
            ext = "mp3" if "mpeg" in prev.get("media_type", "audio/mpeg") else "wav"
            (d / f"{name}.{ext}").write_bytes(base64.b64decode(prev["audio_base_64"]))
            found[name] = prev["generated_voice_id"]
            print(f"{name}: {prev['generated_voice_id']}")
    (d / "previews.json").write_text(json.dumps(found, indent=1), encoding="utf-8")
    print(f"listen in {d}, then: python synth.py eleven-save el-bright <generated_voice_id>")


def cmd_eleven_save(key, generated_id):
    desc = {p["key"]: p["description"] for p in VOICES["eleven"]["prompts"]}.get(key, "Rin audition voice")
    res = json.loads(eleven("/v1/text-to-voice", {
        "voice_name": f"Rin S4 {key}", "voice_description": desc, "generated_voice_id": generated_id}))
    ids = json.loads(ELEVEN_IDS.read_text()) if ELEVEN_IDS.exists() else {}
    ids[key] = res["voice_id"]
    ELEVEN_IDS.write_text(json.dumps(ids, indent=1), encoding="utf-8")
    print(f"saved {key} -> {res['voice_id']}")


def cmd_eleven():
    if not ELEVEN_IDS.exists():
        sys.exit("no saved voices yet; run eleven-design and eleven-save first")
    model = VOICES["eleven"]["model_tts"]
    chars = 0
    for key, vid in json.loads(ELEVEN_IDS.read_text()).items():
        d = OUT / key
        d.mkdir(parents=True, exist_ok=True)
        for line in LINES:
            f = d / f"{line['id']}.mp3"
            if f.exists():
                continue
            text = f"[{VOICES['styles']['eleven'][line['emotion']]}] {line['text']}"
            f.write_bytes(eleven(f"/v1/text-to-speech/{vid}?output_format=mp3_44100_128",
                                 {"text": text, "model_id": model}))
            chars += len(text)
        print(f"{key}: done")
    print(f"credits used this run: ~{chars}")


# ---------- Kokoro (local, Apache-2.0) ----------

def cmd_kokoro():
    import numpy as np
    import soundfile as sf
    from kokoro_onnx import Kokoro
    cfg = VOICES["kokoro"]
    k = Kokoro(str(DIR / cfg["model"]), str(DIR / cfg["voices_file"]))
    for v in cfg["voices"]:
        style = sum(w * k.get_voice_style(name) for name, w in v["mix"].items())
        d = OUT / v["key"]
        d.mkdir(parents=True, exist_ok=True)
        for line in LINES:
            f = d / f"{line['id']}.wav"
            if f.exists():
                continue
            audio, sr = k.create(line["text"], voice=style, speed=cfg["speed"][line["emotion"]], lang="en-us")
            sf.write(f, np.asarray(audio), sr)
        print(f"{v['key']}: done")


# ---------- Chatterbox (local, MIT) ----------

def cmd_chatterbox():
    import inspect
    import torch
    import torchaudio as ta
    cfg = VOICES["chatterbox"]
    torch.set_num_threads(os.cpu_count() or 4)
    r = cfg["reference"]
    ref = DIR / r["file"]
    if not ref.exists():  # ~10 s of the Kokoro blend: synthetic, Apache-2.0
        parts = [ta.load(str(OUT / r["from_voice"] / f"{lid}.wav")) for lid in r["lines"]]
        sr = parts[0][1]
        gap = torch.zeros(1, int(0.3 * sr))
        ta.save(str(ref), torch.cat([x for w, _ in parts for x in (w, gap)], dim=1), sr)
    models = {}
    for v in cfg["voices"]:
        if v["model"] not in models:
            if v["model"] == "turbo":
                from chatterbox.tts_turbo import ChatterboxTurboTTS as M
            else:
                from chatterbox.tts import ChatterboxTTS as M
            models[v["model"]] = M.from_pretrained(device="cpu")
        model = models[v["model"]]
        accepted = inspect.signature(model.generate).parameters
        d = OUT / v["key"]
        d.mkdir(parents=True, exist_ok=True)
        for line in LINES:
            f = d / f"{line['id']}.wav"
            if f.exists():
                continue
            exag, cfg_w = cfg["expr"][line["emotion"]]
            kw = {"exaggeration": exag, "cfg_weight": cfg_w}
            kw = {k: val for k, val in kw.items() if k in accepted}
            if v["ref"]:
                kw["audio_prompt_path"] = str(ref)
            t0 = time.time()
            wav = model.generate(line["text"], **kw)
            ta.save(str(f), wav, model.sr)
            print(f"  {v['key']} {line['id']}: {time.time() - t0:.1f}s for {wav.shape[-1] / model.sr:.1f}s audio", flush=True)
        print(f"{v['key']}: done", flush=True)


# ---------- listening page ----------

def cmd_manifest():
    voices = []
    for d in sorted(p for p in OUT.iterdir() if p.is_dir() and p.name != "eleven-design"):
        files = {f.stem: f"out/{d.name}/{f.name}" for f in d.iterdir() if f.suffix in (".ogg", ".mp3", ".wav")}
        if files:
            voices.append({"key": d.name, "files": files})
    (OUT / "manifest.json").write_text(json.dumps({"lines": LINES, "voices": voices}, indent=1), encoding="utf-8")
    print(f"{len(voices)} voices -> out/manifest.json")


# ---------- S3 held-out check (Thai-accent English) ----------

def cmd_heldout():
    s3 = DIR.parent / "s3-stt"
    spec = json.loads((s3 / "tools" / "heldout_phrases.json").read_text(encoding="utf-8"))
    out = s3 / "recordings" / "azure"
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for voice in spec["voices"]:
        vtag = voice.split("-")[2].replace("Neural", "").lower()
        for rate in spec["rates"]:
            rtag = "normal" if rate == "+0%" else "slow"
            for intent, phrases in spec["phrases"].items():
                for i, p in enumerate(phrases, 1):
                    cid = f"{intent.lower()}-{vtag}-{rtag}-{i}"
                    f = out / f"{cid}.wav"
                    if not f.exists():
                        ssml = ("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='th-TH'>"
                                f"<voice name='{voice}'><prosody rate='{rate}'>{escape(p)}</prosody></voice></speak>")
                        f.write_bytes(azure_tts(ssml, "riff-16khz-16bit-mono-pcm"))
                    rows.append(json.dumps({"id": cid, "intent": intent, "cond": f"azure-{vtag}-{rtag}",
                                            "question": "", "file": f.name, "ref": p}))
        print(f"{voice}: done")
    (out / "manifest.jsonl").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} clips -> {out}")


if __name__ == "__main__":
    load_env()
    cmds = {"azure-voices": cmd_azure_voices, "azure": cmd_azure, "eleven-design": cmd_eleven_design,
            "eleven-save": cmd_eleven_save, "eleven": cmd_eleven, "kokoro": cmd_kokoro, "chatterbox": cmd_chatterbox, "manifest": cmd_manifest,
            "heldout": cmd_heldout}
    if len(sys.argv) < 2 or sys.argv[1] not in cmds:
        sys.exit(__doc__)
    cmds[sys.argv[1]](*sys.argv[2:])

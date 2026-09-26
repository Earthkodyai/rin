# Spike S4: Rin's voice. Result: GO (ElevenLabs "Rin soft", one paid month in Phase 4)

**Date:** 2026-09-26/27 · **Prototype:** [`spikes/s4-voice/`](../../spikes/s4-voice/) (throwaway; `synth.py`, blind rating page `listen.html`; audio, models and keys git-ignored)
**Pass bar (phase 0 plan):** the user is happy with the voice, and the vendor's terms allow it in a free, publicly distributed app.

**Summary.** Round 1: eight adult female English voices read the same 10 lines. The user rated them blind (letters A–H, shuffled). All three voices shortlisted were ElevenLabs. The user picked **"Rin soft"**, a voice they designed with ElevenLabs Voice Design (3.9/5). The premade "Jessica" scored 4.0/5 but is used by many other apps. No Kokoro voice made the shortlist. Round 2 added three Chatterbox voices (MIT, with emotion control): none made the shortlist, and Rin soft beat Jessica 4.0 to 3.0. The free plan forbids commercial use. ElevenLabs' Help Center says output made during a paid month keeps its commercial license after cancelling, so one paid month in Phase 4 is enough.

## Candidates
| Source | Voices | Notes |
|---|---|---|
| ElevenLabs, designed by the user in the web UI (free plan) | Rin bright, **Rin soft** | Voice Design through the API is paid-only (403 `feature_not_available`); the web UI works on free. TTS through the API works on free with `eleven_v3` and inline tags such as `[softly]` |
| ElevenLabs premade | Jessica, Sarah | Shared by every ElevenLabs user |
| Kokoro-82M, local ([Apache-2.0](https://huggingface.co/hexgrad/Kokoro-82M)) via kokoro-onnx (MIT), int8 | af_heart, af_bella, af_nicole, heart+bella blend | $0 and no account. No style control; emotion was approximated with speed. ~18 s per line on this laptop's CPU |
| Azure AI Speech | *(not tested)* | Azure for Students refused: the ku.th SSO is not enabled for Microsoft ("app_not_enabled_for_user"), then "not eligible for an Azure free account". The user declined pay-as-you-go |

## Method
1. **Ten lines**, one per app emotion (friendly, excited, pouty, amused, cheerful, proud, gentle, concerned, curious, warm), including Rin's name and "Hmm". Written before any audio was made ([`lines.json`](../../spikes/s4-voice/lines.json)).
2. **Blind, two rounds.** Round 1: three lines per voice, and the user shortlists. Round 2: all ten lines per shortlisted voice, rated 1–5 stars. Names were shown only after rating.
3. Design prompts for the two designed voices are in [`voices.json`](../../spikes/s4-voice/voices.json) (`el-bright`, `el-soft`). The user generated previews in the web UI and saved one voice per prompt.

## Results
| Voice | Shortlisted | Mean stars (10 lines) |
|---|---|---|
| ElevenLabs Jessica (premade) | yes | 4.0 |
| **ElevenLabs Rin soft (designed)** | yes | **3.9** (3 on L06 "proud", 4 on the rest) |
| ElevenLabs Rin bright (designed) | yes | 3.0 |
| ElevenLabs Sarah, Kokoro ×4 | no | – |

Ratings were almost flat per voice, so they reflect the voice as a whole, not each line. Jessica and Rin soft are a tie in practice. Rin soft won on identity: it's unique to this app and fits the plan's rule of an originally designed synthetic voice. ElevenLabs credits used: about 4.9k of the free 10k.

## Round 2: free open-source voices with emotion control (2026-09-27)
The user asked whether a free model from GitHub could replace the paid voice. The laptop has no NVIDIA GPU (Intel Iris Xe, i5-1145G7, 15 GB RAM), so only CPU-sized models were in reach. Licenses were checked first. F5-TTS, Fish Audio, Voxtral and XTTS are non-commercial, so they were out. Orpheus 3B (Apache-2.0) and Hume TADA (Llama 3.2 license) need a GPU and were deferred.

**Chatterbox** (Resemble AI, MIT; outputs carry an inaudible Perth watermark), CPU, emotion set per line with `exaggeration` 0.35–0.8 and `cfg_weight` 0.3–0.5:
- `cb-default`: original model with its built-in voice.
- `cb-kkmix`: original model cloning a 10 s clip of the Kokoro heart+bella blend. The reference was synthetic and Apache-2.0: never a real person, and never ElevenLabs free-plan output.
- `cbt-kkmix`: Chatterbox-Turbo cloning the same clip.

Speed on this CPU: original ~35 s per line, Turbo ~15 s per line (clips 3–5 s). Lengths were sane, with no run-on or truncated clips.

A new blind round (new letters, separate ratings) put these three against Rin soft and Jessica. **No Chatterbox voice was shortlisted.** Rin soft 4.0 (every line), Jessica 3.0. Rin soft is now ahead of Jessica, not tied, which confirms the pick.

## Decisions and what they change
1. **O1 voice vendor: ElevenLabs, voice "Rin soft"**. License checked against ElevenLabs' own Help Center (2026-09-27), so no support email is needed:
   - [What happens to my content after my subscription ends?](https://elevenlabs.io/docs/help-center/account/general/what-happens-to-my-content-after-my-subscription-ends): output made during a paid subscription keeps its commercial license "forever" after the subscription ends.
   - [Can I publish the content I generate?](https://elevenlabs.io/docs/help-center/legal/can-i-publish-the-content-i-generate-on-the-platform): paid plans include a commercial license, except for content made with **Beta Services**, which can't be used commercially or in production. Content made outside a paid subscription can't be used commercially.
   - `eleven_v3` has no alpha/beta label on the [models page](https://elevenlabs.io/docs/overview/models). Third-party sources date its GA to Feb–Mar 2026.
   - **Rules for the Phase 4 build month:** generate every line and every retake during the paid month; download everything before the billing cycle ends; confirm in the UI that the model shows no beta/alpha badge; keep the invoice and the generation history as evidence of dates. Any later fix needs another paid month.
2. **Budget:** the user agreed to one paid month when the Phase 4 voice pack is built: Starter $6 (30k credits, about 350 lines) or Creator's first month $11 (121k credits, room for regenerations). Nothing is paid before Phase 4.
3. **Lip sync comes from the audio, not vendor visemes** (decided during S4). Azure HD voices and ElevenLabs send no visemes, and live chat needs the same path anyway. The voice pack stores a `mouth` track made at build time.
4. **Live chat (v1.1) has a voice-consistency cost.** Rin's live replies must use the same voice, which means ongoing ElevenLabs usage. This goes to O7 (AI budget).
5. **Free fallbacks, if ElevenLabs' terms change before Phase 4:** neither Kokoro nor Chatterbox made a shortlist, so the next thing to try is Orpheus 3B on a free Kaggle GPU. Paying monthly is the other option.

## Not tested (carry forward)
- The S3 Azure Thai-voice held-out check. Without Azure it can't run. The S3 verdict didn't depend on it.
- Azure voices, including whether HD voices run on the free F0 tier.
- Tags that ElevenLabs may read aloud instead of acting on. None were reported in listening. Check again on the full pack.
- Rin soft across 300–500 lines: consistency, pronunciation of names and numbers, loudness after −16 LUFS normalization.

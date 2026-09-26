# Spike S3: offline English STT. Result: GO for suggested replies (Vosk per-screen grammar), NO-GO for free speech

**Date:** 2026-09-26 · **Device:** Xiaomi 14T, Android 15 / HyperOS 2.0, airplane mode · **Prototype:** [`spikes/s3-stt/`](../../spikes/s3-stt/) (throwaway; harness `s3.sh`, summary `summarize.py`, matcher ablation `RescoreTest`)
**Pass bar (phase 0 plan):** the main intent is recognized in ≥ 90% of answers, offline.

**Summary.** The tester is the app's first user, a Thai speaker of English. Free spoken answers reach at best 77% (part A), so free speech can't be the main input. Part A led to a design change: Rin shows three suggested replies and the user says one. That design was then tested on a new recording that was frozen in git before it was made and never used for tuning (part B). Vosk limited to the on-screen words got **48/48** (in-list 42/42, 95% CI 91.6–100%), and it rejected all 6 off-list lines. Google's on-device recognizer got 87.5–89.6%, and the live mic test offline got 8/8.

## Method
1. **Record once, replay to every engine.** The tester's WAVs (16 kHz mono) are streamed at real-time pace into each engine. They stay on the phone and in a git-ignored folder; only transcripts are committed. Each set has 48 items: 24 with the phone in hand, 16 lying down and mumbling sleepily, 8 with the phone about 1 m away.
2. **Engines.** Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Google's on-device service, fed through `EXTRA_AUDIO_SOURCE`), with and without `EXTRA_BIASING_STRINGS` or peak normalization. Vosk 0.3.75 with `vosk-model-small-en-us-0.15`: free vocabulary, a grammar of the intent keywords (part A), or a grammar of the three on-screen replies (part B).
3. **Everything that scores was frozen before the speech it scores**: intent matcher v1 before part A was recorded; the part B replies and chip matcher in commit `fb7bea4`; the off-list lines in `edd8b9c`. Commits and tests are in `spikes/s3-stt/`.
4. **Part A is the dev set**: error taxonomy, then one fix at a time (ablation). Its tuned numbers are optimistic. **Part B is held out**: nothing was tuned on it.
5. A smoke set (Windows SAPI voices, 32 clips) checked the pipeline first. Android on-device got 31/32 and Vosk 32/32, so the harness works.

Common Voice was considered as a held-out set and dropped. It has no Thai-accent English subset, the full English release is hundreds of GB, and it is read-aloud sentences rather than replies. A second synthetic held-out set (Azure Thai voices reading English, list frozen in `0d64f23`) will run once S4 has an Azure account. It adds other voices; the verdict above does not depend on it.

## Part A: free answers (dev set)
Each prompt named the intent to express (8 intents × 6), not the words.

| Engine | Intent accuracy (v1 matcher) | 95% CI | Normal / Sleepy / Far | Latency after speech ends, p50 / p95 |
|---|---|---|---|---|
| Android on-device | 31/48 = 64.6% | 50–77% | 14/24 · 11/16 · 6/8 | 722 / 864 ms |
| Android on-device + biasing | 31/48 = 64.6% (identical text) | | | 722 / 868 ms |
| Vosk free vocabulary | 26/48 = 54.2% | | 13/24 · 8/16 · 5/8 | 957 / 1144 ms |
| Vosk keyword grammar | 31/48 = 64.6% | | 17/24 · 11/16 · 3/8 | 742 / 888 ms |
| **Vosk keyword grammar, peak-normalized to −3 dBFS** | **37/48 = 77.1%** | **64–87%** | 19/24 · 12/16 · 6/8 | 744 / 886 ms |

Ablation on the same 48 answers:
| Step | Android on-device | Android → Vosk grammar when Android is empty |
|---|---|---|
| v1 matcher (frozen) | 64.6% | 70.8% |
| + phrase coverage from error analysis ("want to sleep" → snooze) | 66.7% | 72.9% |
| + Thai-accent-aware sound key (vowel length, v→w, th→t, Thai final consonants) | 68.8% | 75.0% |
| + both | 70.8% | 77.1% |
| Audio: 1 s lead-in silence | 58.3% (worse) | |
| Audio: peak normalize | 66.7% | |

Every engine and every fix fails the 90% bar. The best result, 77.1%, has a 95% upper bound of 86.7%, so this isn't a small-sample miss.

Why Android missed (v1):
| Cause | Count | Examples |
|---|---|---|
| Empty transcript on one-word answers | 7 | "No", "Hi", "Okay" → nothing. Vosk grammar caught all three "No"s. |
| Accent substitutions or misheard | 7 | "seek" for sick (×2), "Um see", "I'm feels", "M5" (×2), "Hello hello" for an agreeing answer |
| Matcher gap | 1 | "You want to sleep" read as tired, not snooze |
| Ambiguous label | 2 | Asked for an off-topic reply, the tester said "I don't want to speak right now" and "I want to sleep". Those are real answers (refuse, snooze), so the prompt design was at fault. |

## Part B: suggested replies (held out)
8 screens of Rin's lines, each with 3 replies (for example "I'm up!", "Five more minutes", "I feel sick"). The tester said the highlighted reply as written (42 items), or a fixed line that isn't on the screen (6 items, e.g. "What time is it?"). The chip matcher picks the reply whose content words were heard (≥ 50%, no tie) or none.

| Engine | All 48 (95% CI) | Said a reply: correct (95% CI) | Off-list rejected | Normal / Sleepy / Far (in-list) | Latency p50 / p95 |
|---|---|---|---|---|---|
| Android on-device | 42/48 = 87.5% (75–94%) | 36/42 = 85.7% (72–93%) | 6/6 | 17/20 · 11/14 · 8/8 | 790 / 896 ms |
| Android on-device + peak normalize | 43/48 = 89.6% (78–96%) | 37/42 = 88.1% (75–95%) | 6/6 | 17/20 · 12/14 · 8/8 | 788 / 886 ms |
| **Vosk, grammar = the 3 on-screen replies** | **48/48 = 100% (92.6–100%)** | **42/42 = 100% (91.6–100%)** | **6/6** | 20/20 · 14/14 · 8/8 | 819 / 950 ms |
| Vosk, same, + peak normalize | 48/48 | 42/42 | 6/6 | same | 816 / 958 ms |

- **Android misses only short first replies**: "I'm up!" heard as "I'm" (×2), and "Good morning!", "Yes, please", "Thank you!" returned empty (×3–4). This is the same one-word weakness as in part A.
- **Vosk's off-list rejections are real.** Its grammar includes `[unk]`, and for all 6 off-list lines it output only `[unk]` (plus one stray "was"), so no reply was matched. An empty result looks the same as silence, which is fine because both lead to the same reprompt.
- **Live mic, offline (the real app path, Android on-device):** 8/8 correct, with the final text 1–5 ms after `onEndOfSpeech` (this excludes the recognizer's own silence wait). The live path returned text through normal `onResults`, unlike the external-audio path (finding 4). Vosk wasn't tested on the live mic.
- **Protocol notes, kept for honesty.** (1) In the first take of the 6 off-list items, the tester read "say something not on the screen" as "say one of these", so those items measured nothing. The screen now spells out a line to say (`edd8b9c`); the other 42 items weren't re-recorded, and the first take is kept git-ignored. (2) The first live test ran with the network on (7/8, with 2 `RECOGNIZER_BUSY` from a double tap). It was discarded and redone offline. The app now refuses to start the live test while online.

## Findings that change the real app
1. **Phase 4 uses suggested replies.** Rin's line shows 2–3 short replies. The user says one or taps it; after an empty or unmatched result Rin asks once more, then only the buttons remain. No mission step may require voice.
2. **Recommended engine (O10): Vosk with a per-screen grammar.** It is the only engine above the bar here, it rejects off-list speech through `[unk]`, and the grammar is simply the words on screen. Its cost is +41 MB model and +10 MB arm64 native library, 0.9 s to unpack and load, and 16 KB-aligned libraries (OK for Play). Android on-device adds 0 MB and stays useful as a check, but it drops short replies. The Azure-voice held-out run in S4 is a check across other voices before this is final.
3. **Write replies with 2+ content words.** Every Android miss was a short reply. Vosk handled them, but longer replies ("Okay, I'm getting up") give any engine more to match.
4. **If Google's recognizer is used, onboarding must download en-US.** This phone had only Thai. `triggerModelDownload` installed en-US in about 2.7 min with no prompt, over Wi-Fi. Check `checkRecognitionSupport` and fall back if it's missing.
5. **Google's on-device service with external audio (`EXTRA_AUDIO_SOURCE`) puts the final text in a partial result** flagged `final_result=true`, and `onResults` arrives empty. The live-mic path used normal `onResults`. Handle both.
6. **Biasing strings changed nothing, lead-in silence made things worse, and peak normalization helped slightly.** Apply gain normalization before recognition.
7. **Below Android 12 there's no on-device recognizer API**, and the default recognizer returned `LANGUAGE_UNAVAILABLE` offline. This feeds O8 (minSdk). Vosk works there too, which argues for Vosk.

## Not tested (carry forward)
- The Azure Thai-voice held-out run (frozen list, needs the S4 account).
- Vosk on the live mic with its own endpointing, and Vosk memory (PSS) while loaded.
- Other phones and other speakers; noise from a running alarm or fan; replies close in sound to each other on one screen.

## Tooling notes
- Folders created by `adb shell mkdir` under `Android/data/<pkg>` are invisible to the app. The app must create them first.
- Two `SpeechRecognizer` instances bound to the same service: destroying one kills the other's connection (`ERROR_SERVER_DISCONNECTED`, 11). Probe support on the same instance.
- `s3.sh replay` clears logcat when it starts, so save live-test logs first. The offline live log was rebuilt from console output (noted in the file).
- The Windows SAPI voices (David, Zira) make a free 16 kHz smoke set: `s3.sh smoke`.

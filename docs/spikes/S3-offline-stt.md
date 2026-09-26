# Spike S3: offline English STT. Result: NO-GO for free speech as the main input (dev set); held-out pending

**Date:** 2026-09-26 · **Device:** Xiaomi 14T, Android 15 / HyperOS 2.0, airplane mode · **Prototype:** [`spikes/s3-stt/`](../../spikes/s3-stt/) (throwaway; harness `s3.sh`, summary `summarize.py`, matcher ablation `RescoreTest`)
**Pass bar (phase 0 plan):** main intent recognized in ≥ 90% of answers, offline.

## Method
1. **Record once, replay to every engine.** The tester (the app's first user, a Thai speaker of English) answered 48 prompts from Rin in their own words. Each prompt named the intent to express (8 intents × 6), not the words. Conditions: 24 phone in hand, 16 lying down and mumbling sleepily, 8 with the phone about 1 m away. The WAVs (16 kHz mono) stay on the phone and in a git-ignored folder. The same audio is then streamed at real-time pace into each engine.
2. **Engines.** Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Google's on-device service, fed through `EXTRA_AUDIO_SOURCE`), with and without `EXTRA_BIASING_STRINGS`; Vosk 0.3.75 with `vosk-model-small-en-us-0.15`, both free vocabulary and a grammar limited to the intent keywords.
3. **Intent matcher v1** (keywords + 1-edit fuzzy + negation) was written and unit-tested **before any speech was recorded**, so the baseline measures the recognizer, not a matcher tuned to the data.
4. **Error taxonomy, then one fix at a time (ablation)** on these 48 clips, which are the *dev set*. Tuning on the dev set makes its numbers optimistic.
5. **Held-out set for the verdict:** a phrase list frozen in git before tuning (commit `0d64f23`, [`tools/heldout_phrases.json`](../../spikes/s3-stt/tools/heldout_phrases.json)), read by Azure's Thai neural voices in English. This waits for the Azure account from S4. Common Voice was considered and dropped: it has no Thai-accent English subset, the full English release is hundreds of GB, and it is read-aloud sentences rather than replies.

A smoke set (Windows SAPI voices, 32 clips) checked the pipeline first: Android on-device 31/32 and Vosk 32/32. So the harness works, and the drop below comes from the speech itself.

## Results on the dev set (48 answers, offline)
| Engine | Intent accuracy (v1 matcher) | 95% CI | Normal / Sleepy / Far | Latency after speech ends, p50 / p95 |
|---|---|---|---|---|
| Android on-device | 31/48 = 64.6% | 50–77% | 14/24 · 11/16 · 6/8 | 722 / 864 ms |
| Android on-device + biasing | 31/48 = 64.6% (identical text) | | | 722 / 868 ms |
| Vosk free vocabulary | 26/48 = 54.2% | | 13/24 · 8/16 · 5/8 | 957 / 1144 ms |
| Vosk keyword grammar | 31/48 = 64.6% | | 17/24 · 11/16 · 3/8 | 742 / 888 ms |
| **Vosk keyword grammar, audio peak-normalized to −3 dBFS** | **37/48 = 77.1%** | **64–87%** | 19/24 · 12/16 · 6/8 | 744 / 886 ms |

Ablation, accuracy on the same 48 answers:
| Step | Android on-device | Android → Vosk grammar when Android is empty |
|---|---|---|
| v1 matcher (frozen) | 64.6% | 70.8% |
| + phrase coverage from error analysis ("want to sleep" → snooze) | 66.7% | 72.9% |
| + Thai-accent-aware sound key (vowel length, v→w, th→t, Thai final consonants) | 68.8% | 75.0% |
| + both | 70.8% | 77.1% |
| Audio: 1 s lead-in silence | 58.3% (worse) | |
| Audio: peak normalize | 66.7% | |

**Verdict: the dev set fails the 90% bar with every engine and every fix.** The best result is 77.1%, and the upper end of its 95% interval (86.7%) is still below 90%, so this isn't a small-sample miss. Latency is fine: every engine returns within about 0.9 s of the end of speech.

## Why answers fail (Android misses, v1)
| Cause | Count | Examples |
|---|---|---|
| Empty transcript on one-word answers | 7 | "No", "Hi", "Okay" → nothing. Vosk grammar caught all three "No"s. |
| Accent substitutions or misheard | 7 | "seek" for sick (×2), "Um see", "I'm feels", "M5" (×2), "Hello hello" for an agreeing answer |
| Matcher gap | 1 | "You want to sleep" read as tired, not snooze |
| Ambiguous label | 2 | Asked for an off-topic reply, the tester said "I don't want to speak right now" and "I want to sleep". Those are real answers (refuse, snooze), so the prompt design was at fault. |

## Findings that change the real app
1. **Voice cannot be the main input.** Recognizing free English replies from a Thai speaker at ≥ 90% isn't reachable offline with these engines. Phase 4 should use **suggested replies**: Rin's line shows 2–3 short answers on screen, the user may say one or tap it, one reprompt follows an empty or unknown result, then the buttons. No mission step may require voice. This also matches the Hard rule's offline-first spirit: a failed recognition should cost a tap, not the morning.
2. **Closed-set recognition fits suggested replies.** Vosk's grammar mode, limited to the words on screen, was the best engine here (77% even on open answers). Its weakness is that off-grammar speech gets forced onto the nearest keyword (smoke set: "where did I put my phone" → "wait did i" → snooze), so it needs a confidence or "unknown" guard. Its cost is +41 MB model and +10 MB arm64 native library, 0.9 s to unpack and load, and 16 KB-aligned libraries (OK for Play). Android on-device adds 0 MB but drops one-word answers. The engine is decided after the held-out run (new open decision O10).
3. **Google's on-device English is not preinstalled.** This phone had only Thai. `triggerModelDownload` installed en-US in about 2.7 min with no prompt, over Wi-Fi. Onboarding must do this, check `checkRecognitionSupport`, and fall back if it's missing.
4. **Google's on-device service puts the final text in a partial result.** With external audio, `onResults` arrives with an empty bundle, and the text comes earlier through `onPartialResults` with `final_result=true`. Without handling that, every answer reads as blank.
5. **Biasing strings changed nothing**, and lead-in silence made things worse. Peak normalization helped Vosk by 6 answers, so the real app should apply gain normalization before recognition.
6. **The pre-Android-12 path is unusable offline here.** The default recognizer (Speech Services by Google) returned `LANGUAGE_UNAVAILABLE` offline. This feeds O8 (minSdk): below API 31 there's no on-device recognizer API, so those phones get Vosk or buttons only.
7. **The recording UI cut words.** Many clips end at the last voiced frame because the tester tapped stop at once. In the real app the recognizer owns the mic and endpointing, so this was a spike artifact, but a 300–500 ms tail should be kept if the app ever records.

## Not tested (carry forward)
- **Held-out verdict** (Azure Thai voices, frozen list): needs the S4 account.
- The live mic path in airplane mode (`Live test` button). The replay feeds the same engine, but the real mic and endpointer weren't measured.
- Suggested-reply mode itself: the accuracy when the user says a displayed phrase. The expectation is much higher, but it isn't measured.
- Vosk memory (PSS) while loaded, other phones and accents, noise from a running alarm or fan.

## Tooling notes
- Folders created by `adb shell mkdir` under `Android/data/<pkg>` are invisible to the app. The app must create them first.
- Two `SpeechRecognizer` instances bound to the same service: destroying one kills the other's connection (`ERROR_SERVER_DISCONNECTED`, 11). Probe support on the same instance.
- The Windows SAPI voices (David, Zira) make a free 16 kHz smoke set: `s3.sh smoke`.

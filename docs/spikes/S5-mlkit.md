# Spike S5: camera missions in the tester's home. Result: NO-GO (4/8 on held-out, bar is 5). The main mission becomes a QR sticker; the photo of your own objects is optional

**Date:** 2026-09-27 · **Device:** Xiaomi 14T, Android 15 / HyperOS 2.0 · **Prototype:** [`spikes/s5-mlkit/`](../../spikes/s5-mlkit/) (throwaway; harness `s5.sh`, scoring `tools/summarize.py` (part A) and `tools/score_b.py` (part B))
**Pass bar (phase 0 plan):** at least 5 targets that ML Kit recognizes in ≥ 90% of tries in morning light. Added before any data: no target may be "found" from bed, because the mission exists to get the user out of bed.

**Summary.** Part A tested ML Kit's generic labels (cup, TV, chair…). Only 1 of 8 targets passed, even on the dev set, which the thresholds were tuned on. The tester's room is dark at wake-up, and things near the bed triggered labels. That led to a design change: switch the lights on first, and teach the app your own objects once at setup. Part B tested that design with a rule frozen in git. It got 7/8 on dev but **4/8 on held-out**, one short of the bar. The misses are side, far and walk-up views. One from-bed trial also matched the stairs, because a 15 s bed scan doesn't see everything the bed can see. The photo mission is too unreliable to be the one thing that stops an alarm, so the main mission becomes a QR sticker and the photo mission is optional.

## Method (both parts)
1. **Blind 8 s trials.** Press Start and aim at the target for 8 s. The app streams the live camera (CameraX, 640×480) and logs each frame's labels, confidence, brightness (luma), light sensor lux, timing and, in part B, image embeddings. **No frame is ever written to storage.** The app never shows whether a trial passed; scoring happens on the PC.
2. **Per target: 10 trials.** The app assigns each trial a fixed condition (distance ~40 cm / 1 m / 2 m, from the side, walking up). **From bed: 6 scenes × 6 trials** (bed, pillow, ceiling, wall or floor, bedside, and part B's lights-off check).
3. **Dev, then held-out.** On dev the thresholds are chosen by a rule committed before the data. Held-out is scored with the frozen setting only.
4. **Found** = the target's score reaches the threshold in k consecutive frames within the window. The mission's own timeout would be longer, and the user would see feedback, so these numbers are conservative.

## Part A: ML Kit generic labels (dev only)
Bundled `image-labeling 17.0.9`, 447 labels. The candidate labels per target were frozen before any trial (`tools/labels.json`, commit `1b4b563`), for example Cup/Coffee/Saucer and Shoe/Sneakers. Half the trials used "light as you wake", half used room lights on. Dev ran 2026-09-27 12:41–13:32.

| Target | Lights on (thr 0.5, k 2) | Light as you wake | Fired from bed? |
|---|---|---|---|
| Stairs | 5/5 | 5/5 | no |
| Kitchen sink | 4/5 | 0/5 | no |
| Clock | 4/5 | 1/5 | no |
| TV | 3/5 | 0/5 | no |
| Chair | 5/5 | 4/5 | **9 of 36** (a real chair is visible from bed) |
| Shoes | 4/5 | 0/5 | **6 of 36** (pillows read as "Shoe") |
| Sofa | 0/5 | 0/5 | no |
| Cup | 0/5 | 0/5 | no ("Hand" 0.94 dominates) |

With the pre-registered pick rule (highest hit rate with zero from-bed triggers), only **stairs** passes, 1/8 on dev. Held-out wasn't run: dev is already optimistic, so a held-out couldn't turn 1/8 into 5.

- **Light is the main factor.** "As you wake" trials had a median of 0 lux, with frame luma (after auto-exposure) at a median of 29/255, versus 120 with the lights on.
- **Every home is different.** A label that works in general, like Chair, fails here because a chair is visible from bed. You only find that out in the user's own home.
- **The label set is limiting.** Fridge, door, kettle and rice cooker have no label.
- Protocol note: an earlier dev attempt was wiped by about 40 presses of "Discard last". The button went backwards across targets, and the tester was restarting after a protocol mistake. The complete re-run is what's scored. Part B's Discard removes only the trial just recorded, once.

## Design change (from the tester's question: "shouldn't each user do this, since every home is different?")
- **Lights-on gate.** A frame counts only when the median luma of the last 5 frames is ≥ 70. The value 70 came from part A: 55/58 lit trials against 6/58 wake trials. In the app, Rin asks you to turn the light on first, and light also helps waking up.
- **Teach your own objects at setup.** A 6 s scan per object stores MediaPipe Image Embedder vectors (MobileNetV3, centre square, L2), not photos. Any object works, not just the 447 labels.
- **From-bed scan at setup.** 15 s, lights on. Each object's threshold is **the highest similarity seen in the bed scan + a margin**, so objects that look like something by the bed get a stricter bar.
- One (model, margin, k) for all objects. The app can't tune per object without trials.

## Part B: own objects, lights on (dev, then held-out)
The rule was frozen in commit `448db83` before any part B data. Setup: 8 objects (fridge, front door, kitchen sink, microwave, sofa, stairs, TV, washing machine) and one bed scan, then never redone. Dev ran 15:20–16:01 in afternoon daylight with room lights. The pick was frozen in `4eb8aca`: **small model (4.1 MB), margin 0.17, k 1**. That is the edge of the 7-pass region (0.18 gives 6), which the tie-break chose. Held-out ran 18:21–19:09 after sunset, room lights only. It had been planned for the next wake-up and was moved at the tester's request; no re-teach and no discards.

| Object | Threshold (bed max + 0.17) | Dev | **Held-out** |
|---|---|---|---|
| Fridge | 0.450 | 10/10 | **10/10** ✓ |
| Kitchen sink | 0.523 | 10/10 | **10/10** ✓ |
| Washing machine | 0.411 | 10/10 | **10/10** ✓ |
| Microwave | 0.464 | 10/10 | **9/10** ✓ |
| Stairs | 0.426 | 10/10 | 10/10, but a from-bed wall trial reached 0.463 ✗ |
| Sofa | 0.544 | 10/10 | 6/10 ✗ |
| TV | 0.549 | 9/10 | 6/10 ✗ |
| Front door | 0.766 | 6/10 | 4/10 ✗ (bed max 0.60: a door is visible from bed) |
| **Objects passing** | | **7/8** | **4/8 → NO-GO** |

- **Light gate:** opened in 110/110 lit trials on both days, and 0/6 lights-off trials on both days.
- **Speed:** ML Kit about 77 ms per frame and both embedders about 36 ms, so about 8.5 analysed frames per second. When found, the median time is 50–90 ms, meaning the first frame usually matches.
- **Where misses come from:** on held-out, the sofa's best similarity per trial was 0.67–0.78 for close and 1 m views, but 0.49–0.57 for side, far and walk-up views (threshold 0.544). For the TV it was 0.53–0.85 close or at 1 m, and 0.40–0.65 for the other views (threshold 0.549). The 6 s teach scan doesn't cover those views.
- **The bed scan underestimates.** On dev, live from-bed trials already went above the bed-scan maximum for 5 of 8 objects (fridge 0.41 against 0.28, stairs 0.35 against 0.26). The margin absorbed it on dev but not for stairs on held-out.
- **For comparison only (not the verdict):** the large model (10.9 MB) at the same margin gets 5/8 on held-out, with sofa 9/10, but TV 4/10 and the same stairs trigger.
- **Room light:** the two days had nearly the same lit-room brightness (lux median 44 and 45, luma 108 in both). So the drop from dev to held-out is mostly about views and aiming, not light. It also shows how noisy 10 trials per object is (9/10 has a 95% CI of 60–98%).

## Decision (tester, 2026-09-27)
1. **Main mission: scan a QR sticker** that the user puts far from the bed. It uses ML Kit barcode scanning, which is on-device and offline. Anti-cheat comes from where the sticker is placed; the app can check this at setup by requiring the scan from bed to fail. **Not measured here**: Phase 3 checks it on the device with the same trial style and pass bar (first-try pass ≥ 90%).
2. **Photo of your own objects is optional.** Setup has a lights-on gate, a guided teach scan with more views (front, left, right, far), and a longer bed scan from more than one spot. There's also a **self-test**: aim 3 times and the object must be found each time, then aim from bed and it must not be found. Only objects that pass can become the day's mission. The mission shows live feedback, and a fallback (QR or walking) is always available.
3. Generic ML Kit labels are dropped as a mission. They fail in the dark, fire from bed, and lack household objects.

## Costs measured (arm64-v8a, inside the spike APK)
| Part | Size |
|---|---|
| MediaPipe tasks native library | 11.0 MB |
| MobileNetV3 small / large embedder | 4.1 / 10.9 MB |
| ML Kit labeling native library + model | 11.0 + 3.0 MB (dropped with the labels) |

The spike APK is 116 MB because it bundles 4 ABIs and both embedders. The optional photo mission costs about 15 MB on arm64 (library + small model); ship it as a Play Feature Delivery module or an App Bundle ABI split. The size of ML Kit barcode scanning gets measured in Phase 3.

## Privacy
Frames stay in memory only. Embeddings describe the user's home, so in the app they stay on the device, are deleted with the object or with "delete my data", and never leave the device. In this spike the raw log with embeddings is git-ignored. The committed `results/trials.jsonl.gz` has labels, brightness and timing only (`tools/public_log.py`).

## Not tested (carry forward)
- QR scanning on this phone, and whether a scan from bed fails (Phase 3).
- The improved teach and bed scans, the self-test, and live feedback. Part B's weaknesses point to them but they aren't measured.
- Other homes, phones and testers; real morning daylight with lights on (held-out ran in the evening); objects whose look changes (a TV on or off, a sofa with things on it).

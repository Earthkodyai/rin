# ADR 0001: Three games with Rin replace the out-of-bed missions

- **Status:** accepted, 2026-09-28 (plan decisions D16, D17)
- **Supersedes:** the mission list of D15 (walk, QR sticker, speak, photo of your own object)

## Context
An alarm in RinAlarm stops only after a mission, so that the user is awake when it goes quiet. The Phase 0 and Phase 3 plan chose missions that prove the user left the bed. Each was measured on the target phone (Xiaomi 14T) with rules frozen before the data:

| Mission | Result | Evidence |
|---|---|---|
| Photo of an object (ML Kit labels, then own-object embeddings) | NO-GO: held-out 4/8, bar 5 | [S5](../spikes/S5-mlkit.md) |
| Walk 30 steps | Android's step sensors counted 0 hand-held walking steps and 31–46 shaking steps; our own accelerometer + gyroscope detector blocks hard shaking (3–6) but a gentle walking-rhythm bob from bed still passes (32) | [3.1](../spikes/3.1-walk-vs-shake.md) |
| QR sticker | With the sticker in the bedroom, the edge of the bed read it at 0.249 of the frame, larger than 3 of 5 natural close scans (0.154–0.274): the frozen rule found no valid size bar. Printing and placing a sticker was also the tester's main complaint | [3.2](../spikes/3.2-qr.md) |

The tester (the app's first user) concluded that the out-of-bed missions were either cheatable or too much work, and asked for games with Rin instead.

## Decision
The core is three games in which Rin visibly takes part, all offline:
1. **Cup shuffle:** Rin slides three cups; pick the one with the ball, three times in a row (chance 1/27).
2. **Colour pads:** a 2×2 red/blue/yellow/green grid; Rin's hand shows a sequence, the user repeats it (lengths 3, 4, 5) with at most 3 s before the first tap and between taps. Too slow or wrong: the screen cuts to Rin scolding gently, then a new sequence.
3. **Repeat after Rin:** three short English sentences with subtitles, recognised by Vosk restricted to the sentence (the configuration S3 measured at 48/48). Two tries each, then tap the words in order; a "Can't talk right now" button switches game.

Walking is removed. The QR mission stays in the code behind a flag. A **wake-up check** five minutes after dismissal (one short colour-pad round within 60 s, or the alarm rings again) carries the "don't go back to sleep" job that the missions were meant to do.

**Architecture rule kept:** the ring path never depends on the WebView. Game rules, randomness, answer checking and sounds are Kotlin (unit-tested); the WebView only acts the game out, and every game has a native 2D fallback when the WebView is missing or crashes.

## Consequences
- **Accepted trade-off:** a game proves the user is awake, not out of bed. The wake-up check and the bed-return signals (phone flat and still, screen off, dark, no steps since dismissal) are the answer to falling asleep again, and Phase 3 exits only if the 14-morning log shows it works.
- Phase 3 grows by about three to four sessions; each task ends with a usable app (colour pads first).
- Speech moves from Phase 4 into Phase 3 with placeholder voice lines until the Phase 4 voice pack; RECORD_AUDIO and Vosk (~50 MB) were already approved.
- The walk and QR work is not lost: the measurements stay in `docs/spikes/`, `AccelStepDetector` becomes a bed-return signal, and the QR mission can return once a placement passes its frozen rule.
- Accessibility: colour pads can be played by ear and without colour vision; the cup shuffle is visual only; speech has a tap fallback.

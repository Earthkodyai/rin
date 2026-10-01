# ADR 0005: Finish v0.1 by 1 October 2026, and list what that leaves undone

- **Status:** accepted, 2026-09-30 (plan decision D25); amended 2026-10-01
- **Amends:** the phase order in [07-phases](../plan/07-phases.md): Phase 6 shrinks, Phase 8 and the long field runs move after the deadline

## Context
At 22:30 on 30 September the developer had one night and one day left. Phases 0 to 3 were done, Phase 4 was at its exit check, and the plan still held Phase 5 (moods and settings), Phase 6 (hardening and limited distribution with 7 days of feedback), Phase 8 (Google Play, with a closed test of 12 people for 14 days) and Phase 9 (portfolio).

Three of those cannot fit in two days whatever is chosen: the 14-night alarm run, 7 days of tester feedback, and Play's 14-day closed test. Limited distribution has a second blocker: the only character model is VRoid's AvatarSample_O, whose licence allows personal, non-profit use only and forbids redistribution and modification. An APK carrying it cannot be given to anyone.

## Decision
The finish line is the app working on the developer's phone, a 60 to 90 second demo video, and this public repository with an English README.

- **4.4:** one morning with no network on the phone, the 7-day no-repeat rule from the existing 800-day simulation test, mouth sync by eye plus the 4.3 measurements.
- **Phase 5:** rest day and sick day buttons and a settings page; the existing per-ring moods stand in for a new state machine.
- **Phase 6:** security and code review, a signed release build with R8 for the developer's phone only, the AI notice, a privacy notice, an overnight battery measurement. No crash reporting.
- **Phase 9:** README, this ADR, the demo video.
- **Deferred and listed in the README:** the 14-night run, limited distribution and its feedback, all of Phase 8, the developer's own Rin model, other speakers for the Repeat after Rin held-out set, the remaining cup held-out rings, dark-room rings, and a UI/UX phase.

**Amended 2026-10-01:** after 6.2 the developer asked that the plan's order be followed rather than skipped, since shortcuts read badly on a résumé. Phase 6 was finished as far as possible (6.3: privacy notice, battery run) before Phase 9 started, and the UI/UX work became its own phase after the deadline instead of quick changes before the video.

## Consequences
- The demo is honest about scale: one phone, one tester, one real morning. The README's "What is not done" section carries every deferred item.
- The sample model never leaves the developer's phone. A release build includes it only when a local, git-ignored flag is set; CI and clones build without it.
- Phase 6 stays open on one item, limited distribution, until the developer's own model exists. Phase 8 follows the UI/UX phase.
- Doing 6.3 in order paid off: the overnight battery dump exposed network traffic charged to the app (WebView Safe Browsing through Google Play services), which was fixed before the privacy notice went public.

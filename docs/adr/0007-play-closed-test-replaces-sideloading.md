# ADR 0007: A Google Play closed test replaces the sideloaded limited distribution

- **Status:** accepted, 2026-10-02 (plan decision D32)
- **Supersedes:** Phase 6's plan to hand a signed APK to up to 20 devices through a free limited-distribution account

## Context
Phase 6 planned v1.0 for friends as a signed APK, installed by hand, through Google's free limited-distribution account (no ID, up to 20 named devices). Phase 8 then planned a separate Google Play closed test: personal Play accounts made after 13 November 2023 need 12 testers opted in for 14 days in a row before they can apply for production.

Three things changed:
- Since 30 September 2026, certified Android phones in Thailand only install apps registered to a verified developer, from any source. A sideloaded APK now needs its package registered too.
- The developer already has a verified personal Play Console account (paid 2026-10-01) with the app entry and its package name reserved, so the free limited-distribution account would only add a second account.
- The UI/UX phase is done and Rin's own model is in, so the build is one that may be shared (the VRoid sample never is, D25).

## Decision
- Testers get the app through a **Google Play closed test**, not a sideloaded APK. The same 14 days count as Phase 6's feedback (its 7 days) and as Phase 8's production requirement (12 testers, 14 days unbroken).
- **Play App Signing uses the developer's existing release key** (exported with Google's PEPK tool), so builds from Play and builds from the developer's PC install over each other without wiping alarms. The key stays outside the repo, backed up on Drive.
- `bundleRelease` refuses a bundle without Rin's own model, her voice pack or the alarm themes, or with the VRoid sample (`checkPlayBundle`).
- Recruiting starts from zero, so the store listing, the content declarations and the tester invitation are drafted first, and the 14-day clock starts when the 12th tester opts in.

## Consequences
- Google reviews the closed-test build before testers can install it, and the listing and the content declarations (content rating, Data safety, target audience 18+, the exact-alarm, full-screen and foreground-service declarations) must be filled in before that, earlier than Phase 8 planned.
- Updates reach testers through Play, and Phase 8 inherits the closed test instead of starting one.
- ADR 0006's open question (does an update from Play reset the full-screen permission on HyperOS?) gets answered by the first update to testers.
- The app signing key is chosen once: moving to a Google-generated key later needs Play's key upgrade. Losing the local copy would stop installs from the PC over the Play build; Play itself can reset an upload key.

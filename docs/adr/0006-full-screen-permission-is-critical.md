# ADR 0006: A missing full-screen permission is critical, and an app update checks for it

- **Status:** accepted, 2026-10-01 (plan decision D26)
- **Supersedes:** the 1.4 rule that a missing full-screen permission is only a Diagnostics warning

## Context
On Android 14 and later an alarm app needs `USE_FULL_SCREEN_INTENT` to show its ring screen over the lock screen. Without it, Android shows a heads-up notification instead: the alarm still sounds, but the user has to unlock the phone to reach the ring screen and the game. In task 1.4 this was rated a warning, shown in Diagnostics only, on the grounds that the sound is unaffected.

During the release-build checks on the Xiaomi 14T (HyperOS 2), the developer allowed the permission, and the next ring still came up as a notification only. The app-ops record showed why: the permission was allowed at 02:58, the app was updated with `adb install -r` at 03:05, and the 03:08 ring was refused (`USE_FULL_SCREEN_INTENT: ignore`). Nobody had touched the setting. It happened again on each of the next three updates: four resets in four updates. Nothing on the main screen said so, because the check was only a warning.

## Decision
- The full-screen check is `CRITICAL`: the main screen shows its red banner ("Alarms may be hidden, late or silent") until the permission is back, and the banner leads to the settings page.
- When the app is updated (`MY_PACKAGE_REPLACED`) and the permission is gone, the app posts one notification, "Your alarm can't show on the lock screen", which opens the app. It only posts when a notification can reach the user.
- The Diagnostics text says the switch can turn itself off after an update, and that without it you must unlock the phone to reach Rin.

## Consequences
- Verified on the phone: update, permission reset, notification shown, tap, banner, permission back on, banner gone, next ring over the lock screen.
- One more notification channel ("Setup problems"). It fires only on an update that broke something, so it is not a nag.
- Unknown until Phase 8: whether an update from Google Play resets the permission the same way. Android 14 limits full-screen intents for apps that are not installed from Play, which may be what triggers the reset here.
- Hidden is one of the three things the banner exists for (hidden, late, silent), so the banner rule now matches its own text.

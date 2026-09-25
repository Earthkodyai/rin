# Spike S1: alarm reliability. Result: GO

**Date:** 2026-09-26 · **Device:** Xiaomi 14T (2406APNFAG), Android 15 / SDK 35, HyperOS 2.0 (OS2.0.208.0.VNEMIDC)
**Prototype:** [`spikes/s1-alarm/`](../../spikes/s1-alarm/) (throwaway; test harness `s1.sh`, raw log `results/ringlog.csv`)

## What was built
`setAlarmClock` → `AlarmReceiver` → foreground service (`systemExempted`) → a generated tone on `USAGE_ALARM` via `AudioTrack` (no media file, so it plays before the first unlock) plus a full-screen intent to `RingActivity` (`showWhenLocked`, `turnScreenOn`). Alarms are stored in device-protected storage and re-registered by a `directBootAware` receiver on `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and a change in exact-alarm permission. Every step writes a CSV row with the scheduled time, the actual time, and a device snapshot (screen, Doze, saver, DND, ringer, alarm volume, exact, FSI).

## Results (pass bar: rings in every case, within ±1 min)
| Case | Fired (ms late) | Sound (ms late) | Full-screen over lock | Result |
|---|---|---|---|---|
| Screen on, ringer silent | 21–37 | 868–1176 | n/a (heads-up) | PASS (heard by user) |
| Screen off + locked | 29–44 | 871–895 | yes | PASS |
| Deep Doze (forced IDLE, alarm 13 min out) | 37 | 194 | yes | PASS* |
| Swiped from Recents (process killed) | 850 | 1747 | yes | PASS |
| Reboot, not unlocked, Autostart off (default) | 550 | 749 | yes | PASS |
| App update (`adb install -r`) | alarm kept by the OS | | | PASS |
| Battery saver on | 28–44 | 135–318 | yes (when FSI allowed) | PASS |
| DND priority (default DND) | 44 | 882 | yes | PASS |
| DND "Total silence" | 35 | muted (`alarmVol=0`) | no | N/A: the platform mutes alarms by design |

16 alarms fired; the latest was **850 ms** late (cold process start after a swipe), far inside ±1 min.
\*Doze: Android refuses deep idle when an alarm clock is due within `min_time_to_alarm=10m`, so an alarm under 10 minutes out can't be tested in Doze. With the alarm 13 min out, the phone held IDLE for about 4.5 min and then dropped to INACTIVE about 8.5 min before firing (cause unconfirmed). A natural overnight run in Phase 1 should confirm this.

## Findings that change the real app
1. **FGS type = `systemExempted`.** Android docs allow it for apps holding `USE_EXACT_ALARM`, and it worked on the device. `mediaPlayback` is not allowed to start from `BOOT_COMPLETED` on Android 15+. Declare it in the Play FGS form.
2. **HyperOS full-screen intent is fragile.** For a sideloaded app, the uid-level `USE_FULL_SCREEN_INTENT` op defaults to `ignore`. The user's toggle via `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` did not take effect. An adb grant worked but reverted after a reboot and unlock. **Sound never depended on it.** The app must check `canUseFullScreenIntent()` at schedule and ring time, warn in onboarding and Diagnostics, and keep the heads-up notification as the fallback. Re-test once the app is installed via limited distribution or Play.
3. **Autostart (MIUIOP 10008) is off by default.** It blocks `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, but **not `LOCKED_BOOT_COMPLETED`**. Direct Boot plus device-protected storage is what makes reboot work without asking the user for Autostart. Keep it mandatory.
4. **Ring audio must be available before unlock.** Rin's voice pack (or at least the first ring clip) must live in device-protected storage, with the generated tone as the fallback.
5. **"Total silence" DND mutes alarms.** Diagnostics should warn when `INTERRUPTION_FILTER_NONE` is on.
6. Fired-to-sound latency is about 0.9 s (FGS start plus audio setup), 1.7 s on a cold process. Fine for waking someone up, but Rin's first line should not assume instant playback.

## Not tested (carry to Phase 1 ring log)
A natural overnight run (Doze for more than 1 hour), force-stop from Settings (by Android design this cancels alarms), time and timezone changes, Ultra battery saver, Bluetooth or headphone routing, the 15-minute auto-stop, and phones from other OEMs.

## Tooling notes (HyperOS)
Over adb, HyperOS blocks `install -g`, `pm grant`, `input` (key/tap), `settings put global` and `cmd power set-mode` unless "USB debugging (Security settings)" is on. Tests on this phone need a person for taps and toggles. Automated tests belong on an emulator.

# STATUS — 2026-09-28 (Phase 1: 14-night run · Phase 2: done on the VRoid sample · Phase 3: 3.1 done, **3.2 QR paused: a Rin game instead of missions is under discussion**)

Phase 1: tasks done; **14-night run** pending (Diagnostics card, Strict daily; set one alarm repeating every day — none enabled as of 28 Sep). Ends at 14/14 + green CI (needs the remote; 0.4 accounts on the user).
Phase 2 **done 2026-09-28** on the VRoid sample (all exit bars, phase-2.md). **Your model still pending** (`rin.model=<path>` in local.properties; ❓ edits in `docs/character/character-sheet.md`); when it lands: `./gradlew checkGestures -PwriteBody` → build → `./gradlew checkGestures` → `tools/character/phase-exit.sh`, and re-check hands (`npm run dev`).
## Next
- **Decide the game pivot** (you raised it 2026-09-28: walking can be gamed, QR too much effort, object photos inaccurate). Advice given in the session; nothing in the plan changed yet. If it goes ahead: new decision in 01-decisions, phase-3.md tasks and exit bar rewritten, 05c updated.
## Done
- **3.2 QR (paused, committed as it stood):** QR mission (`QrMission`, `QrScanner` CameraX 1.6.2 at 1x + bundled ML Kit 17.3.0, auto light, tap to open, 60 s idle close), setup (`ui/qrsetup`: print your own sticker via qrcodegen or use a code you have, salted hash only, 3-spot bed check, "I moved it"), editor QR chip + offer under Rin picks, readiness (camera, set up, unlocked), `QrLabActivity`. 225 unit tests, lint clean. **Open problems + data: docs/spikes/3.2-qr.md** (no valid size bar: edge of bed read 0.249 in the bedroom; placeholder 0.20 would pass from there).
- **3.1** (aab4fb6): mission framework, walk 30 steps (own `AccelStepDetector`; hard shake 3–6, gentle walking-rhythm bob 32 = known limit), ring screen (full-body Rin, 3 s emergency hold). docs/spikes/3.1-walk-vs-shake.md.
- **Phase 3 kickoff** (D15): tone ~15% while progressing, full after 30 s idle · mission per alarm, "Rin picks" only among ready missions · Bond = Phase 5; Phase 3 logs the events.
- **2.1–2.5**, **1.1–1.5**, Phase 0 spikes: see git log, 05a, docs/spikes/.
- Tools: Android Studio 2026.1.4.7, Android CLI, SDK 36/37, gitleaks pre-commit hook, Node 24. git main, local identity Earthkodyai, **no remote** (push needs one). Test phone Xiaomi 14T (serial GUVWEA6TGUORO76D); every adb install needs a tap.
## 14-night run rules
- Count only non-test rings. **`am instrument` / force-stop wipe AlarmManager registrations**: open the app afterwards, check `dumpsys alarm`. Reinstall and `am kill` are safe.
## Carry into later
- Device-untested: incoming call during a ring, overnight Doze, time/TZ change, reboot with the new build; 3.1 tone back to full after 30 s idle; the QR ring-screen flow.
- A mission that is set up but out of reach that morning (QR sticker while travelling) leaves only the emergency hold: needs a no-Bond swap before Phase 5 charges Bond.
- Debug hooks: `DebugAlarmReceiver --es cmd add --ei sec 45 [--es mission qr|walk|none]`; `QrLabActivity --es trial <label>` / `StepLabActivity` (`am start -f 0x10008000`, never force-stop). DB: `adb exec-out run-as <pkg> cat /data/user_de/0/<pkg>/databases/rinalarm.db`.
## HyperOS adb limits
Blocked: install -g, pm grant, input, settings put global. MIUIOP 10020 (Show on Lock screen) must be allowed. Git Bash needs MSYS_NO_PATHCONV=1 for device paths; `run-as <pkg> sh -c '...'` must be quoted as one adb shell argument.
## Tool paths (under %LOCALAPPDATA%)
android: Microsoft\WinGet\Packages\Google.AndroidCLI_Microsoft.Winget.Source_8wekyb3d8bbwe\android.exe · gitleaks: Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe · adb: Android\Sdk\platform-tools\adb.exe · WebView devtools (debug): `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`
## Open decisions
Game pivot (new) · O2 Claude model · O3 Rin's design · O4 app name · O7 AI budget · O9 crash reporting

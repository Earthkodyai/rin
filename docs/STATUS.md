# STATUS — 2026-09-30 (Phase 1: 14-night run pending · Phase 2: done on the VRoid sample · **Phase 3: done** (3.7 simulated run 10/10) · next: Phase 4)

Phase 1: tasks done; **14-night run** pending (Diagnostics card, Strict daily; set one alarm repeating every day — none enabled). Ends at 14/14 + green CI (needs the remote; 0.4 accounts on the user).
Phase 2 **done 2026-09-28** on the VRoid sample. **Your model still pending** (`rin.model=<path>` in local.properties; ❓ edits in `docs/character/character-sheet.md`); when it lands: `./gradlew checkGestures -PwriteBody` → build → `./gradlew checkGestures` → `tools/character/phase-exit.sh`, re-check hands (`npm run dev`), gaze (`aimCamera`, 3.7 finding 1) and the pads hand still (thumb, finding 2).
## Next
- **Phase 4** (voice + offline dialogue): docs/plan/phase-4.md. Needs the paid ElevenLabs month (Rin soft) — ask before paying.
- **Open from Phase 3 (recorded limits, D19):** 4 remaining held-out cup rings (6/6 so far, bar 9/10); dark rings for cups and Repeat after Rin; no real-morning data. 3.5: 2–3 other people should record the held-out protocol (`RepeatLabActivity --es set heldout`, scored by `tools/stt/pc_replay.py`).
- **Résumé gaps:** GitHub account (0.4) so the repo gets a remote and CI goes green; start the 14-night run.
## Done
- **D18 (649c939, ADR 0002):** no wake-up check, no back-to-bed detection; the game ends the alarm. Task 3.6 cut. Reasons: a phone left on the bed and a sleeper beside it look the same; snoozing is the norm; strict self-control tools get abandoned.
- **D19 + 3.7 (549609c … 12d1265):** 3.7 became one simulated run, checklist frozen before the first ring: **10/10 pass**, one `EMERGENCY_STOP` (S7) and no failures. Fixed during it: Rin's gaze in the full-body view (6–12.7° over the user → ≤ 1.8°; head aims at the camera, cancels mood pitch, counters sway; cup table unchanged), the pads hand's thumb (open, angled down), 2D cups vs table contrast (1.07 → ≥ 3:1, `CupsBoardColoursTest`). docs/spikes/3.7-simulated-run.md.
- **3.5 speech, 3.4 cups, 3.3 colour pads, 3.2 QR (paused), 3.1:** see docs/spikes/ and git log. 2.1–2.5, 1.1–1.5, Phase 0 spikes: see git log, 05a, docs/spikes/.
- Tools: Android Studio 2026.1.4.7, Android CLI, SDK 36/37, gitleaks pre-commit hook, Node 24. git main, local identity Earthkodyai, **no remote**. Test phone Xiaomi 14T (serial GUVWEA6TGUORO76D); every adb install needs a tap.
## 14-night run rules
- Count only non-test rings. **`am instrument` / force-stop wipe AlarmManager registrations**: open the app afterwards, check `dumpsys alarm`. Reinstall and `am kill` are safe.
## Carry into later
- Device-untested: incoming call during a ring, overnight Doze, time/TZ change, reboot with the new build; tone back to full after 30 s idle.
- Debug hooks: `DebugAlarmReceiver --es cmd add --ei sec 45 [--es mission pads|cups|speech|none|rin_picks]`, `--es cmd clear` (deletes `smoke` alarms); `DebugCharacterReceiver --es cmd crash` (her page → 2D fallback). DB: `adb exec-out run-as <pkg> cat /data/user_de/0/<pkg>/databases/rinalarm.db` (+ `-wal`, `-shm`).
- Preview gaze/hand checks: `rin-character-web` in C:\.claude\launch.json; the hidden pane throttles frames and screenshots can be stale, so step with screenshots and read state via JS.
## HyperOS adb limits
Blocked: install -g, pm grant, input, settings put global. MIUIOP 10020 (Show on Lock screen) must be allowed. Git Bash needs MSYS_NO_PATHCONV=1 for device paths; `run-as <pkg> sh -c '...'` must be quoted as one adb shell argument.
## Tool paths (under %LOCALAPPDATA%)
android: Microsoft\WinGet\Packages\Google.AndroidCLI_Microsoft.Winget.Source_8wekyb3d8bbwe\android.exe · gitleaks: Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe · adb: Android\Sdk\platform-tools\adb.exe · WebView devtools (debug): `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`
## Open decisions
O2 Claude model · O3 Rin's design · O4 app name · O7 AI budget · O9 crash reporting

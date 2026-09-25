# STATUS — 2026-09-26

Phase: 0 in progress. 0.1–0.3 done, S1 done (GO). Next: S2 (WebView + three-vrm perf), then S3–S5. 0.4 accounts still on the user.

## Done
- Plan v1.0 in docs/plan/ (index README.md). Tools installed (Android Studio 2026.1.4.7, Android CLI 1.0 with --no-metrics, SDK 36, platform-tools 37.0.1). JDK = Android Studio JBR: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- 9 Google Android skills in .claude/skills/; .claude/settings.json allowlist + binary/build Read denies; Unity plugin hidden via settings.local.json.
- git (main), local identity Earthkodyai (noreply), MIT LICENSE (code only), gitleaks pre-commit hook. No remote yet.
- Test phone: Xiaomi 14T, Android 15/SDK 35, HyperOS 2.0, serial GUVWEA6TGUORO76D. Every adb install needs a tap on the phone.
- **S1 GO**: docs/spikes/S1-alarm-reliability.md, prototype spikes/s1-alarm/ (harness s1.sh, results/). 16 alarms, max 850 ms late. Decisions table updated in 01-decisions.md.

## Carry into Phase 1 (from S1)
- FGS type systemExempted; Direct Boot + device-protected storage mandatory; first ring audio must exist pre-unlock.
- HyperOS full-screen intent op defaults to ignore / reverts; check canUseFullScreenIntent() at schedule+ring, Diagnostics warning, heads-up fallback. Ask user what the "Full-screen perm" settings page showed.
- Untested: overnight natural Doze, Settings force-stop, time/timezone change, Ultra saver, BT routing, 15-min auto-stop.

## HyperOS adb limits
Blocked without "USB debugging (Security settings)": install -g, pm grant, input, settings put global, cmd power set-mode. `appops set --uid <pkg> USE_FULL_SCREEN_INTENT allow` works but reverts. Git Bash needs MSYS_NO_PATHCONV=1 for device paths.

## Tool paths (not on PATH in Claude's shell)
android: %LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.AndroidCLI_Microsoft.Winget.Source_8wekyb3d8bbwe\android.exe
gitleaks: %LOCALAPPDATA%\Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe
adb: %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

## Open decisions
O1 voice vendor (after S4) · O2 Claude model (Phase 7 eval) · O3 Rin's design · O4 app name · O7 AI budget unlock · O8 minSdk (after S1–S3; S1 used 26 without issue) · O9 crash reporting

## Parking lot
(none yet)

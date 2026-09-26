# STATUS — 2026-09-26 (after S3)

Phase: 0 in progress. 0.1–0.3 done, S1 GO, S2 GO (Plan A), S3 dev-set NO-GO for free speech (held-out waits for Azure). Next: S4 (needs Azure account), then S3 held-out, S5. 0.4 accounts still on the user.

## Done
- Plan v1.0 in docs/plan/ (index README.md). Tools installed (Android Studio 2026.1.4.7, Android CLI 1.0 with --no-metrics, SDK 36, platform-tools 37.0.1). JDK = Android Studio JBR: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- 9 Google Android skills in .claude/skills/; .claude/settings.json allowlist + binary/build Read denies; Unity plugin hidden via settings.local.json.
- git (main), local identity Earthkodyai (noreply), MIT LICENSE (code only), gitleaks pre-commit hook. No remote yet.
- Test phone: Xiaomi 14T, Android 15/SDK 35, HyperOS 2.0, serial GUVWEA6TGUORO76D. Every adb install needs a tap on the phone.
- **S1 GO**: docs/spikes/S1-alarm-reliability.md, prototype spikes/s1-alarm/ (harness s1.sh, results/). 16 alarms, max 850 ms late. Decisions table updated in 01-decisions.md.
- **S2 GO**: docs/spikes/S2-webview-perf.md, prototype spikes/s2-webview/ (s2.sh, summarize.py, results/). VRoid sample 18.4 MB: ~119 fps (120 Hz cap), load 1.1–1.2 s (1.93 s first after install). Model file git-ignored.
- **S3**: docs/spikes/S3-offline-stt.md, prototype spikes/s3-stt/ (s3.sh, summarize.py, RescoreTest). User's 48 answers (git-ignored recordings/): Android on-device 64.6%, Vosk grammar+norm 77.1%, none reaches 90%. Held-out phrase list frozen (0d64f23); run it with Azure th-TH voices after S4 (`tools/heldout_phrases.json`, still needs a synth script). Live-mic test not done.

## Carry into Phase 4 (from S3)
- Suggested replies on screen (say or tap), one reprompt, then buttons; no step needs voice. Onboarding downloads Google en-US on-device pack. Final text arrives as partial with final_result=true. Peak-normalize audio. O10 engine choice after held-out.
## Carry into Phase 2 (from S2)
- Memory ~620–720 MB, mostly textures → atlas + KTX2, model <= 10–15 MB, dispose off-screen. Pixel ratio cap 2, fps cap 30–60, PNG poster while loading, warm WebView after install/update, bridge = addWebMessageListener.
## Carry into Phase 1 (from S1)
- FGS type systemExempted; Direct Boot + device-protected storage mandatory; first ring audio must exist pre-unlock.
- HyperOS full-screen intent op defaults to ignore / reverts; check canUseFullScreenIntent() at schedule+ring, Diagnostics warning, heads-up fallback. Ask user what the "Full-screen perm" settings page showed.
- Untested: overnight natural Doze, Settings force-stop, time/timezone change, Ultra saver, BT routing, 15-min auto-stop.
## HyperOS adb limits
Blocked without "USB debugging (Security settings)": install -g, pm grant, input, settings put global, cmd power set-mode. `appops set --uid <pkg> USE_FULL_SCREEN_INTENT allow` works but reverts. Git Bash needs MSYS_NO_PATHCONV=1 for device paths (unset it for gradlew; `cygpath -w` for adb install).
## Tool paths (not on PATH in Claude's shell; under %LOCALAPPDATA%)
android: Microsoft\WinGet\Packages\Google.AndroidCLI_Microsoft.Winget.Source_8wekyb3d8bbwe\android.exe
gitleaks: Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe
adb: Android\Sdk\platform-tools\adb.exe
## Open decisions
O1 voice vendor (after S4) · O2 Claude model (Phase 7 eval) · O3 Rin's design · O4 app name · O7 AI budget unlock · O8 minSdk (after S1–S3; <31 has no on-device STT API) · O10 STT engine (after S3 held-out) · O9 crash reporting

# STATUS — 2026-09-27 03:00 (S5 data collection)

Phase: 0 in progress. 0.1–0.3 done, S1–S4 done (see below). **S5 running**: app installed, smoke OK. Next: user runs session "dev" on the 1st morning → `./s5.sh summary dev` → copy results/picked-dev.json to tools/frozen.json, commit → session "heldout" on the 2nd morning → `./s5.sh summary heldout --config tools/frozen.json` → write docs/spikes/S5-mlkit.md, update 01-decisions.md, close Phase 0. 0.4 accounts still on the user.

## Done
- Plan v1.0 in docs/plan/ (index README.md). Tools installed (Android Studio 2026.1.4.7, Android CLI 1.0 with --no-metrics, SDK 36, platform-tools 37.0.1). JDK = Android Studio JBR: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- 9 Google Android skills in .claude/skills/; .claude/settings.json allowlist + binary/build Read denies; Unity plugin hidden via settings.local.json.
- git (main), local identity Earthkodyai (noreply), MIT LICENSE (code only), gitleaks pre-commit hook. No remote yet.
- Test phone: Xiaomi 14T, Android 15/SDK 35, HyperOS 2.0, serial GUVWEA6TGUORO76D. Every adb install needs a tap on the phone.
- **S1 GO**: docs/spikes/S1-alarm-reliability.md, prototype spikes/s1-alarm/ (harness s1.sh, results/). 16 alarms, max 850 ms late. Decisions table updated in 01-decisions.md.
- **S2 GO**: docs/spikes/S2-webview-perf.md, prototype spikes/s2-webview/ (s2.sh, summarize.py, results/). VRoid sample 18.4 MB: ~119 fps (120 Hz cap), load 1.1–1.2 s (1.93 s first after install). Model file git-ignored.
- **S3**: docs/spikes/S3-offline-stt.md, prototype spikes/s3-stt/. Free answers (dev, 48): best 77.1%. Suggested replies (held-out, frozen fb7bea4/edd8b9c): Vosk chips 48/48, Android 87.5–89.6%, live mic offline 8/8. Recordings git-ignored. Azure check list: tools/heldout_phrases.json (needs a synth script).
- **S5 (in progress)**: spikes/s5-mlkit/ (s5.sh, tools/summarize.py, labels.json frozen 1b4b563). Blind 8 s trials, labels only (no frames saved); 10 trials/target, 6/from-bed negative; pass = 5 targets ≥ 90% with 0 negative triggers on held-out. Smoke: 57 ms/frame, ~17 fps, ~115 KB log/trial (commit only results/trials.jsonl.gz). APK 51 MB all ABIs: measure arm64-only for the write-up.
- **S4 GO**: docs/spikes/S4-voice.md, prototype spikes/s4-voice/ (synth.py, listen.html, preview config rin-s4-listen :8765). Blind round 1 (8 voices): Rin soft 3.9, Jessica 4.0, Rin bright 3.0, Kokoro none. Round 2 (Chatterbox x3, venv .venv-cb): none shortlisted; Rin soft 4.0 > Jessica 3.0. Voice id el-rin-soft in out/eleven_voices.json (git-ignored). License verified from official Help Center (no email): pay 1 month in Phase 4, generate + download all lines in that month, no beta models. Azure untestable (student offer refused) → S3 Azure check skipped. Lip sync from audio.
## Carry into Phase 4 (from S3)
- Suggested replies (say or tap), 1 reprompt, then buttons; replies with 2+ content words. O10 decided: Vosk per-screen grammar (+~50 MB).
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
O1 ElevenLabs Rin soft (done) · O2 Claude model (Phase 7 eval) · O3 Rin's design · O4 app name · O7 AI budget unlock · O8 minSdk (Vosk works on any API level) · O9 crash reporting

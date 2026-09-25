# STATUS — 2026-09-26

Phase: 0 in progress. 0.1–0.3 done; next is 0.4 (user accounts) and spikes S1–S5.

## Done
- Plan v1.0 split into docs/plan/ (index README.md). User started Phase 0.
- Installed: Android Studio 2026.1.4.7 (elevated, winget), Android CLI 1.0 (winget, always run with --no-metrics), SDK at %LOCALAPPDATA%\Android\Sdk: platforms/android-36, build-tools 36.0.0, platform-tools 37.0.1 (adb). SDK license accepted by the CLI.
- 9 official Google Android skills in .claude/skills/ (project scope): android-cli, android-intent-security, android-permissions-security, android-profiler, camerax, edge-to-edge, play-policy-insights, r8-analyzer, testing-setup. Note: android-profiler's trace_processor and play-policy-insights' scraper download/fetch from the network when run.
- .claude/settings.json: allow gradlew/adb/android/git read cmds; deny Read of *.vrm/*.vrma/*.ogg/*.png/build/. .claude/settings.local.json: syncClaudeAiPlugins=false (hides the Unity plugin here; re-enable for Plan B).
- git repo (main), local identity Earthkodyai <241633768+Earthkodyai@users.noreply.github.com>, .gitignore, .gitattributes (LF), MIT LICENSE (code only; assets all rights reserved), gitleaks 8.30.1 pre-commit hook (.git/hooks, not versioned).

## Next
1. User: create the free limited-distribution account (Android Developer Console) and enable 2FA on GitHub. Tell the user a GitHub remote is not created yet (ask before creating/pushing).
2. User: enable Developer options + USB debugging on the phone, connect USB; read OEM/model via adb (O5).
3. Spike S1 (docs/plan/phase-0.md), then S2–S5.

## Tool paths (not on PATH in Claude's shell)
android: %LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.AndroidCLI_Microsoft.Winget.Source_8wekyb3d8bbwe\android.exe
gitleaks: %LOCALAPPDATA%\Microsoft\WinGet\Packages\Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe\gitleaks.exe
adb: %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

## Open decisions
O1 voice vendor (after S4) · O2 Claude model (Phase 7 eval) · O3 Rin's design · O4 app name · O5 test phone (read via adb) · O7 AI budget unlock · O8 minSdk · O9 crash reporting

## Parking lot
(none yet)

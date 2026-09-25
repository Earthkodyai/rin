# RinAlarm — operating notes for Claude Code

Start every session by reading `docs/STATUS.md`, then only `docs/plan/phase-N.md` for the current phase. Open the files it lists under "อ่านประกอบ" only when the task needs them; never read all of `docs/plan/`. The plan is in Thai; this file stays English and short because it loads on every request.

## Product
Android alarm app with one original 3D anime companion, "Rin" (friendly morning buddy, not romantic). She wakes the user in English, gives an out-of-bed mission, and reacts emotionally. Portfolio project for SE internships: tests, CI, docs and measured numbers matter as much as features.

## Hard rules
- The ring path is native-only (AlarmManager.setAlarmClock -> foreground service / full-screen intent -> USAGE_ALARM audio). It must never depend on network, WebView or AI.
- Offline-first. Every AI feature has an offline fallback. Live AI chat stays behind a feature flag; AI budget is currently $0 (off).
- No API keys or secrets in the app or the repo. Treat the repo as public from the first commit; gitleaks must pass.
- Photos: on-device ML Kit only, in memory, never saved or uploaded. Mic only during an active conversation, with a visible indicator.
- Content: SFW, Rin is clearly an adult, AI disclosure always visible, crisis protocol (Thai hotline 1323), Bond penalties bounded and recoverable.
- Ask the user before: elevated installs (UAC never prompts on this PC), persisting a Git identity, changing Claude settings or plugins, adding dependencies that need new permissions, anything that costs money.

## Stack
Kotlin + Jetpack Compose, Hilt, Room/DataStore, Media3, CameraX, ML Kit. Character: WebView + three.js + @pixiv/three-vrm, local assets via WebViewAssetLoader. Backend (Phase 7): Cloudflare Workers + TypeScript + Anthropic SDK. targetSdk 36; minSdk decided in Phase 0.

## Token hygiene
- One task per session. Finish by updating `docs/STATUS.md` (<= 30 lines), then /clear. Wrap up before context reaches ~200k.
- Quiet builds (`./gradlew <task> -q`), logcat filtered by tag, never paste full logs.
- Screenshots only for visual checks; prefer UI dumps and tests.
- Never Read binary assets (*.vrm, *.vrma, *.ogg, *.png) or build/ directories.

## Layout (planned)
app/ Android · web/character/ three-vrm (Vite) · tools/voice/ voice-pack pipeline · backend/ Workers (Phase 7) · docs/ plan/, STATUS, adr/, spikes/

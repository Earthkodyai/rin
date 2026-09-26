# Spike S2: WebView + three-vrm performance. Result: GO (Plan A)

**Date:** 2026-09-26 · **Device:** Xiaomi 14T (2406APNFAG), Dimensity 8300-Ultra (MT6897), Mali-G615 MC6, 12 GB RAM, 120 Hz 1220×2712 (DPR 3.25), Android 15 / HyperOS 2.0 · **WebView:** 153.0.8010.36
**Prototype:** [`spikes/s2-webview/`](../../spikes/s2-webview/) (throwaway; harness `s2.sh`, summary `summarize.py`, raw log `results/runs.log`)

## What was built
A native Activity hosts a WebView that loads a Vite + TypeScript page from APK assets through `WebViewAssetLoader` (`https://appassets.androidplatform.net`, no `file://`). The page reports back through `WebViewCompat.addWebMessageListener`, which is scoped to that origin. The page uses three.js 0.186.1 and @pixiv/three-vrm 3.5.5 to load a VRoid Studio 2.3.0 sample (AvatarSample_O, exported as VRM 1.0 with default settings). It applies `removeUnnecessaryVertices`, `combineSkeletons` and `combineMorphs`, then runs a procedural idle loop that stands in for the real app: breathing, head and spine sway, blink, a happy/relaxed cross-fade, the `aa` mouth, lookAt and all spring bones. Each run is a cold start (`am force-stop` then `am start`). Load time is measured from `Activity.onCreate`, before the WebView is created, to the first rendered frame with the model. fps is measured over 30 s after a 3 s warm-up.

Model: 18.4 MB `.vrm` (stored uncompressed in the APK), 18 meshes, 37k triangles, 30 PNG images → 37 GPU textures, 98 morph targets, 132 spring joints, 27 draw calls.

## Results (pass bar: average fps ≥ 45 and load ≤ 2.5 s)
| Setting (3 cold runs each) | Canvas px | Load to first frame | Avg fps | 1% low fps | Frames > 33 ms | App PSS (graphics) | Renderer PSS |
|---|---|---|---|---|---|---|---|
| Pixel ratio capped at 2 (planned) | 750×1668 | 1.10–1.23 s | 119.0–119.5 | 59–66 | 0 | 361–366 MB (255–260) | 279–312 MB |
| Native DPR 3.25 (worst case) | 1218×2710 | 1.10–1.20 s | 119.2–119.4 | 60–64 | 0 | 407–409 MB (301) | 281–313 MB |
| Pixel ratio 1.5 | 562×1251 | 1.10–1.18 s | 119.1–119.7 | 60–72 | 0 | 342–350 MB (244–249) | 281–313 MB |
| **First launch after install** (cap 2) | 750×1668 | **1.93 s** | 119.9 | 80 | 0 | 357 MB (254) | 276 MB |
| 3-minute run (cap 2) | 750×1668 | — | 119.3 | 61 | 0 (max 24.9 ms) | 362 MB (260) | 265 MB |

**Both criteria pass with a wide margin**: frame rate is pinned at the 120 Hz display limit (p50 8.3 ms, p99 8.5–16.6 ms) and the slowest load was 1.93 s. The 1% lows around 60 fps are single missed vsyncs at 120 Hz (16.6 ms), not visible hitches. The 3-minute run showed no throttling: battery 36.0 → 37.1 °C, thermal status 0 throughout (about 12 minutes of continuous rendering in total).

Load breakdown, first launch vs later: WebView create 150 / 80–120 ms · JS start 0.2 s · fetch 0.09 s · VRM parse 0.30–0.35 s · **shader compile + texture upload 0.96 / 0.35–0.40 s**. The likely cause is Chromium's on-disk GPU program cache, so only the first launch pays the full compile (inferred, not verified).

## Findings that change the real app
1. **Plan A (WebView + three-vrm) is confirmed.** Unity as a Library stays Plan B and is not needed on this class of device.
2. **Memory is the real cost, not fps.** App PSS plus the renderer is about 620–720 MB, and 250–300 MB of that is GPU memory, mostly the 30 uncompressed textures. That's fine on 12 GB. On a 4 GB phone it risks the low-memory killer, and the app process also hosts the ring path. Phase 2 actions: export Rin with a texture atlas at 2048 or smaller, convert textures to KTX2 (ASTC/ETC2 via `KTX2Loader`) to cut GPU memory by about 4×, keep the model at 10–15 MB or less, and `dispose()` the scene when the character is off screen.
3. **Cap the pixel ratio at 2.** Native DPR costs about 45 MB more graphics memory and 2.6× the pixels for no visible gain at arm's length.
4. **Cap the frame rate.** The WebView renders at 120 fps by default, which is wasteful for an idle character. Throttle to 30–60 fps in the render loop (or use `Surface.setFrameRate`) to save battery and heat. There's plenty of headroom to do this.
5. **The first frame after install or a WebView update is about 0.8 s slower** (shader compile). The ring path is native-only anyway (hard rule). Rin should fade in over a static PNG poster, and the app can warm the WebView once after install or update so the first real alarm hits the cache.
6. `addWebMessageListener` with an origin allowlist works on this WebView and is the bridge to use; `addJavascriptInterface` is not needed.

## Not tested (carry to Phase 2)
- **Lower-end GPUs.** This is an upper-mid device. Frame-rate headroom beyond the 120 Hz cap wasn't measured. Keep the PNG fallback and a quality tier (pixel ratio 1, spring bones off).
- The first launch after a reboot (cold page cache), a transparent WebView composited over Compose, real VRMA clips, audio and lip sync running at the same time, measured battery drain, and memory with KTX2 textures.
- Rin's own model: the numbers above use a VRoid sample, and Rin's final texture and bone count may differ.

## Tooling notes
- `adb shell am start --es q "a&b"` is run by the device shell, so `&` must be quoted (`"'a&b'"`).
- Git Bash: `MSYS_NO_PATHCONV=1` (needed for device paths) breaks `gradlew` and local paths given to `adb install`. Unset it for Gradle and pass `cygpath -w` paths.
- A 3-minute run can outlast the logcat buffer. Read results as they arrive rather than all at the end.
- The model file is git-ignored (`spikes/s2-webview/model/`). It isn't our asset, and its VRM metadata says redistribution is prohibited.

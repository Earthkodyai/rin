# Rin model check (vroid-check)

## Round 1 — 2026-10-01, `Rin.vrm` (name Rin, author Earthkodyai, version 1.0)

**Machine checks** (`tools/character/check-vrm.mjs --author Earthkodyai`): PASS
- VRM 1.0, licence meta as the brief asks, author matches, every expression and smile morph, every gesture bone.
- WARN raw export 16.5 MB; after the pipeline's KTX2 step it is **12.1 MB** (limit 15 MB), texture memory 192 → 46 MB. (The first run reported this as a failure; the checker now applies the limit to the shipped file, as the Gradle pipeline does.)
- WARN 191 spring-bone joints, above the sample's 179 (the most measured on the 14T): measure fps when it goes into the app.

**Licence of the parts:** started from **New** with VRoid Studio PC presets only (the user, 2026-10-01). Presets may be used, commercially too, unless an item shows special terms ([VRoid help](https://vroid.pixiv.help/hc/en-us/articles/4405813333657-Can-I-use-the-models-created-with-VRoid-Studio-Stable-Ver-for-commercial-purposes), [guidelines](https://vroid.com/en/studio/guidelines)). No Booth items.

**Human checks** (stills in `build/vroid-check`, not committed):
- Looks almost exactly like VRoid's AvatarSample_O (purple twin tails, black dress with white frilled bib, bow, puff sleeves, laced shoes); only the green eyes differ. Texture bytes match only VRoid's generic shader images, so the files cannot tell; the user confirmed it was built from New with presets.
- Reads fairly young (Claude's observation); the adult look is the user's call.
- Outfit is modest, but a gothic dress where the brief asks for sleep or home wear.
- **The user's decision:** small tweaks: change the hair colour and switch the outfit to home wear, keep the face and body. Re-export and check again (round 2).

## Round 2 — 2026-10-01, `Rin.vrm` (name Rin, author Earthkodyai, version field still 1.0)

**Machine checks:** PASS. WARN raw 16.4 MB (round 1 compressed to 12.1 MB); WARN **200** spring-bone joints (up from 191 with the new long loose hair): measure fps in the app.

**Human checks:** hair is now long, loose and pink with bright green eyes, clearly different from AvatarSample_O. The outfit and shoes are unchanged (black gothic dress, laced shoes).

**The user's decision:** keep this outfit for now and put the model into the app (2026-10-01). The adult look and the outfit are the user's calls; the brief's home-wear suggestion stays open for the UI/UX phase.

## In the app — 2026-10-01

`rin.model=D:/VRoid/Rin/Rin.vrm`; `rin.devModelInRelease` removed, so the release APK carries only `rin.vrm` (12.2 MB after KTX2, texture memory 46 MB).

**Phase 2 exit numbers on the 14T (phase-exit.sh):** load 1.45–1.75 s (bar 2.5 s) · 30 fps cap 29.3 fps, 0 frames over 50 ms · uncapped 119.4 fps (bar 45; 200 spring joints are fine) · moods 20/20 within 300 ms (slowest 249 ms) · renderer crash: still took over, same pid · memory app 350 MB + renderer 200 MB.

**Fixes the user's eye drove:**
- **Arms bent out at rest:** her arm (forearm + hand bone) is 0.435 m, 1.2× the sample's 0.363 m that every key was written for. Hand targets now scale by arm length (`armScale` in vrma.mjs); poles and twists do not.
- **Pout's folded arms went through her body and skirt** (14 mm after scaling; raising or moving the arms made it worse or turned the pose into arms held out). Sulks play `huff` on the ring screen too (`Gesture.onRing`); checkGestures lists pout as `skip (not played)`.
- **Skin washed to white:** lights at 0.7 of the old rig (directional 0.7π + ambient 0.28π), picked by the user from renders at 1.0 / 0.8 / 0.65 / 0.5 and on the phone. VRoid shows the skin cream and shaded.
- **Mouth gaped at the start of lines:** (1) opening compressed (square root, cap 0.6: line starts went from 13% to 7% wider than the rest, frame jumps −25%); (2) the head-tap reaction raised VRoid's `happy` (Fcl_ALL_Joy, eyes shut and mouth wide) at the moment her line starts; it now raises the eyes-open `smile` and steps back while she talks. The user confirmed both head taps and the hello.
- **Open:** two lines once logged `clip=false` with the clips in the APK (not seen again); LineVoice now opens a clip missing from its list before going silent and logs a warning. `clap` and `yawn` brush the skirt (22–23 mm "frill") during the move: watch on the phone.

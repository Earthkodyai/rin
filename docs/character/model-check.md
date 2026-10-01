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

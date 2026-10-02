# Play Console: App content answers (task 6.5, D32)

Drafted from the code and PRIVACY.md on 2026-10-02. The user enters these in Play Console (Policy and programs → App content) and submits; Claude can fill them through Claude in Chrome only with the user's approval for each submit. Every "No" below must stay true: re-check if the code changes.

| Section | Answer | Why (evidence) |
|---|---|---|
| Privacy policy | https://earthkodyai.github.io/rin/privacy/ | 6.3; the app shows the same text |
| Ads | **No, my app does not contain ads** | no ad SDK, no INTERNET permission |
| App access | **All functionality is available without special access** | no account or login; the mic game asks for the mic like any permission |
| Content rating (IARC) | Category **All other app types** (utility). Violence, fear, sexuality, language, controlled substances, crude humour: **none**. Users interact or share content: **No**. Shares location: **No**. Digital purchases: **No**. Gambling: **No**. Unrestricted internet/browser: **No**. Expected result: 3+ / Everyone | Rin is SFW and clearly adult (05e); her teasing is mild ("Too slow~") and has no swearing |
| Target audience and content | **18 and over only** | D4; the app isn't directed at children (PRIVACY.md "Children") |
| Appeals to children? | **No** | |
| News app | **No** | |
| Health apps | **None of these** | an alarm, not health/sleep tracking |
| Government app | **No** | |
| Financial features | **None** | |
| Data safety | **No data collected or shared.** Encryption in transit: not applicable (nothing is sent). Deletion: no account, uninstall or clear storage | No INTERNET permission (PrivacyTextTest checks the manifest). Mic audio is processed on the phone in memory and discarded, which Play does not count as collection. The ring log stays on the phone. Backup off |
| Exact alarms (`USE_EXACT_ALARM`) | Core function: **alarm clock** | the app is an alarm clock |
| Full-screen intent (`USE_FULL_SCREEN_INTENT`) | Core function: **alarm** (shows the ring screen over the lock screen) | allowed for calling and alarm apps |
| Foreground service | Type **systemExempted**: the ring keeps sounding and the game runs while the alarm rings, until it is dismissed, snoozed or passed. Needs a **video link** (YouTube unlisted) showing an alarm ringing from the lock screen and being stopped by a game | RingService, `foregroundServiceType="systemExempted"` (exempt as an exact-alarm app) |
| Microphone (`RECORD_AUDIO`) | Asked only when the user picks Repeat after Rin, on-device recognition, nothing recorded | if Play asks for a description |
| Generative AI | The app generates nothing at run time; Rin's lines and voice were made with AI before release and ship as fixed files. No in-app AI reporting flow is needed because users never receive generated output | PRIVACY.md "Rin's voice and lines"; D21 one-way voice |

## Things the user must do themselves

- **The FGS video:** record the screen (HyperOS screen recorder) of a test ring from the lock screen through a game to the end. The alarm sound is not captured (Android limit), which is fine: the video shows the use. Upload unlisted to YouTube.
- **Contact email** for the listing (listing.md).
- **Submit** each section.

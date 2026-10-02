# ADR 0008: Difficulty levels and an online university tournament

- **Status:** accepted, 2026-10-02 (plan decision D33)
- **Amends:** the "offline-only, no backend" rule (CLAUDE.md, 6.1 security review), for the tournament only; the "No pouting" setting (Phase 5)

## Context
After the UI/UX phase the developer asked for four things: difficulty levels (today's games become Easy; Normal, Hard and a Nightmare that most people fail even at full effort); a way to cut Rin's lines and to switch her scolding off from the ring screen; an editor that remembers the last level picked; and a tournament of the pads and cup games that starts at Nightmare and keeps getting harder, ranked as a contest between Thai universities.

Until now the app has had no INTERNET permission at all (removed in 6.1), and its Data safety answer is "no data collected". A ranking across phones needs a server.

## Decision
- **Levels:** Easy / Normal / Hard / Nightmare per alarm, for the pads and cup games. Longer sequences, faster demos, shorter answer windows, and bigger boards (3×3 pads from Hard; 4 cups at Hard, 5 at Nightmare). Easy keeps every timing frozen in 3.3/3.4. Repeat after Rin keeps one level, because its errors come from the microphone, not from the player. New users start at Normal; existing alarms become Easy; the editor remembers the last level picked. Nightmare may be set on a real alarm, with a warning; the emergency stop still works.
- **Rin's lines:** any button cuts the line she is saying and does its job. The scold has a switch on the ring screen; off silences her at once and becomes the alarm's setting and the default for new alarms, until it is switched back on in the editor. It replaces the "No pouting" setting.
- **Tournament:** a separate mode, entered from the home screen or from a button shown in the last 5 s of the ring screen. One miss ends a run; the score is levels passed, then less thinking time.
- **Leaderboard:** Firebase on the free Spark plan: Firestore, anonymous auth, App Check with Play Integrity, and security rules that validate every field and accept only a better score from its own user. Names are optional and filtered; every row can be reported; players can delete their scores. The network is used only by the tournament module; the ring path stays native and offline.
- **University logos:** the developer chose the real logos with a "not affiliated or endorsed" notice and an agreement before play, after being told that a player's agreement grants no rights to a university's trademark. The logo files stay outside the public repo, a missing file falls back to an abbreviation badge, and a takedown request is met by removing the file.

## Consequences
- The app gains the INTERNET permission. The privacy notices (EN + TH), the Data safety form and PrivacyTextTest change: name, university, scores and an anonymous ID go to Google Firebase, outside Thailand, with consent (PDPA).
- Public names are user-generated content under Play policy: a filter, reporting and the developer's removal through the Firebase console are required.
- Scores come from the phone, so a determined cheater with a passing Play Integrity verdict can still post a false score. Rules bound the values; this is a recorded limit.
- Nightmare's claim ("most people fail") must be measured before it is stated anywhere (task G.4).
- Running cost stays $0 within Spark's free quotas; going over them stops writes rather than billing.
- A university could ask for its logo to be removed, or Play could act on a trademark complaint; the badge fallback keeps the feature working if that happens.

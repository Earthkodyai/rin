# ADR 0003: Rin speaks one way; no after-game chat and no live AI chat

- **Status:** accepted, 2026-09-30 (plan decision D21)
- **Supersedes:** the conversation parts of D2 (hybrid offline + live chat), D3 (the user answers in English), D7 and D8 (live-chat budget and model eval), the after-game chat of D20, and Phase 7

## Context
D20 planned Phase 4 as a short conversation: after the game, Rin would ask one or two questions, the user would answer by voice or by tapping one of four suggested replies (Vosk limited to the on-screen words, as S3 required), and she would reply. Phase 7 would later add a live chat through a Claude backend.

The 4.1 script draft made the cost concrete: 24 questions, 48 bound answers and 9 shared pools (tired, sick, thanks, greet, unknown, skip…), about 110 of 318 lines. Reviewing it, the tester rejected the idea itself, for two reasons:

1. **Nobody stays to chat after the alarm stops.** The game already did its job; the user wants to get on with the morning.
2. **The replies are forced anyway.** Offline STT only works on the words shown on screen (S3: free answers from the tester's voice reached 77%), so "talking" means reading one of four buttons aloud. That is a menu, not a conversation.

The tester extended the same reasoning to Phase 7's live chat.

## Decision
Rin talks to the user; the user does not talk back to her. After the won line she says one short remark that ends with a goodbye: the day, the sky in words that fit any weather (she can't know it), or why the next meal matters, picked by the ring time (`after.remark`, `after.meal.*`), and the morning is over. The microphone is used only inside the Repeat-after-Rin game. Phase 7 (backend, live chat, model eval) is cut.

## Consequences
- The script drops to 215 lines + R01–R30 = 245 clips, 9,318 characters (≈3.2× fits the 30k-credit paid month, up from 2.3×).
- Task 4.2 becomes a line selector (pools, 7-day no-repeat, moods) with subtitles and alarm ducking; no intent matcher and no chat screen. The Phase 4 exit drops "chat after the game".
- Rest and sick days (Phase 5) are switched on in settings, not detected from an "I feel sick" reply.
- **Crisis protocol:** with no free input, the app cannot detect a crisis, so there are no crisis voice lines. The 1323 hotline stays as fixed text in the app; where it goes is decided in Phase 5/6. The disclosure badge ("AI") stays on Rin at all times.
- $0 running cost for good: no backend, no API keys, no per-user quota or cost-attack surface, and nothing to say about chat data in the privacy policy. O2 (model choice) and O7 (AI budget) close.
- **Portfolio cost:** the LLM eval (Opus 5 / Sonnet 5 / Haiku 4.5 on 60–100 morning scenarios, red-team set, latency and cost) was planned as a résumé highlight and interview story. It is gone with Phase 7. If an AI piece is wanted later, it can come back as a separate project or as an offline tool (for example an eval of script-writing prompts), without putting a network call in the app.
- If testers later ask to talk to Rin, the D20 design (suggested replies + Vosk grammar) and the Phase 7 plan remain in git history (a1a25cb, c8eb804).

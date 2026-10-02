# Task G.4: one band of difficulty per level (pads and cups)

**Device:** Xiaomi 14T, HyperOS 2 · **Build:** debug, Rin's own model · **Code:** `mission/PadPaths.kt` (pads), `mission/CupsGame.kt` `fair` + `shapedSwaps` (cups)
**Why:** after G.2–G.3 the user found Hard and Nightmare uneven: some rounds were far easier than the next because of easy patterns (cups swapped with the cup beside them too often, the same pad pressed again and again).

## Deviation from the plan (2026-10-03, the user's call)
The plan had the user and 2–3 friends play the locked rules, one change allowed, then a held-out set (target: Nightmare passed within 3 min in ≤ 20% of rounds, Easy ≥ 95%). **There is no time for other players.** Instead: find what makes these games hard in published research, measure the current draws against it in simulation, change the draws, and the user plays them. So the pass-rate targets are **not measured**, and the levels are judged by one player. Recorded as a limit.

## What the research says
- **Sequences of places (Corsi block tapping):** a sequence is harder the longer its path and the more often the path crosses itself, and much easier when it forms a shape (a line, symmetry, Gestalt grouping), which people chunk ([Corsi's block-tapping test: some characteristics of the spatial path which influence memory, PubMed 15141901](https://pubmed.ncbi.nlm.nih.gov/15141901/); [structure, crossings and path length, Frontiers in Psychology 2016](https://frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2016.01686/full)).
- **Repeats (the Ranschburg effect):** an item repeated **right away** is recalled *better*; an item that comes back after two or more others is recalled *worse* ([Ranschburg effect](https://en.wikipedia.org/wiki/Ranschburg_effect); visual-spatial and tactile versions: [Bournemouth University](https://eprints.bournemouth.ac.uk/24475/)). G.2 added immediate repeats at Hard and Nightmare to make them harder; they made them easier.
- **Following a cup (multiple-object tracking):** eyes lose a target when it passes close to look-alikes; speed matters mostly through those close passes (Franconeri et al. 2010, *Tracking multiple objects is limited only by object spacing, not by speed, time, or capacity*). The shell-game benchmark names swap distance, adjacent vs far swaps and swaps without the ball among its difficulty knobs ([Can Vision-Language Models Solve the Shell Game?, arXiv 2603.08436](https://arxiv.org/abs/2603.08436)).

## The draws before (simulated, 20,000 sequences each; `PadsGame`/`CupsGame` as of 1a7e3d3)
| Pads | a pad twice in a row | A-B-A | path never crosses | pads that differ (median) | path length p10–p90 |
|---|---|---|---|---|---|
| Normal 2×2, 6 pads | 0% | 80% | 76% | 4 | 5.0–6.2 |
| Hard 3×3, 7 pads | 50% | 40% | 54% | 5 | 6.2–11.1 |
| Nightmare 3×3, 9 pads | 61% | 51% | 33% | 6 | 8.8–14.5 |

| Cups (balanced, G.3) | swaps between side-by-side cups | ball's cup moved | ball's cup in a row (p90) |
|---|---|---|---|
| Normal, 3 cups | 67% | 67–69% | 4 |
| Hard, 4 cups | 25% | 58–65% | 3–4 |
| Nightmare, 5 cups | 33–34% | 54–55% | 3–4 |

## The draws now (above Easy; Easy unchanged, its seeds replay)
**Pads** (`PadsRules.shaped`, Normal and up; G.2's `repeats` and the hand's lift are gone):
- never a pad twice in a row, never A-B-A, never the same pair of pads twice, never three pads on a line, never three sides round the 2×2 board turning one way;
- a pad comes back at most as often as the board forces (twice in up to 9 pads on 3×3), always with at least two others between;
- the whole path in one band: mean step ≥ 1.5 pads (3×3) / 1.1 (2×2), crossings between `minCrossings` and 2 more (3×3: ⌊(n−4)/2⌋, so 9 pads cross 2–4 times; 2×2: 1 from 6 pads);
- drawn by rejection: a median 1–2 draws, 14 for a 20-pad tournament round.

**Cups** (`CupsRules.shaped`, replaces `balanced`):
- far pairs weigh more (weight = slots apart); the ball still ends anywhere with even odds (the end slot is drawn first);
- the ball's cup moves in 40–60% of the swaps, never more than 2 in a row; at most half the swaps (and half the ball's) are side by side; with as many swaps as cups, every cup moves.

| Cups now (3,000 shuffles each) | side by side | ball's side by side | ball moved | ball in a row (max) | best blind guess |
|---|---|---|---|---|---|
| Normal, 3 cups | 50–55% | 40–47% | 58–62% | 2 | 0.36 (1/3) |
| Hard, 4 cups | 15% | 13% | 47–49% | 2 | 0.26 (1/4) |
| Nightmare, 5 cups | 19–21% | 15–18% | 42–46% | 2 | 0.21 (1/5) |

Three cups have one far pair (0–2), and it cannot come twice in a row, so Normal cannot go below about half. Far swaps pass through the cup standing between them (a known limit since G.3); they are now more common.

Tests: `PadsLevelsTest` (rules, band, shapes rejected), `CupsLevelsTest` (`fair` on every level, fewer side-by-side swaps, even odds per level), `PadsReplayTest` (a logged game replays from seed + trace under the new draws; the G.2 rings' seeds now draw other games). 396 unit tests.

## The user's check
- **Nightmare pads ring (2026-10-03 01:38):** "harder now". 1/3 rounds in 129 s, 11 wrong + 2 timeouts, emergency stop; most misses on pads 5–6 of the 7-pad round.
- **Nightmare cups ring (01:41):** harder, but **the alarm tone jumped to full mid-game**. Log: TONE_QUIET at 01:41:10, TONE_FULL at 01:42:32 (gain 0.15 → 1.0). Only right answers counted as activity (plan phase-3), so 30 s of misses read as "drifted off". Fixed: every judged answer, right or wrong, counts; pad timeouts still do not, so someone asleep still gets the full tone (`ColourPadsMission`, `CupShuffleMission`).
- **Hard pads ring (01:50):** passed in 61 s, one miss; the tone stayed quiet through the misses. But **it got loud as the game started**: Rin's intro hushes the tone only while her clip plays, and the game's first activity came after the intro, so for ~0.6 s the tone was back at full. Fixed: the Let's play tap itself counts as activity (`RingViewModel.markActive`).
- **Hard cups ring (01:53):** passed clean in 28 s.
- **Normal pads ring (01:55):** passed in 61 s, 2 misses (both in the 6-pad round). TONE_QUIET at the Let's play tap (01:55:24.451) and no TONE_FULL after: both tone fixes hold.
- **Normal cups ring (01:57):** passed clean in 23 s.
- **The user's verdict (2026-10-03): "every level is OK".** G.4 done; the rules above are locked for G.5.

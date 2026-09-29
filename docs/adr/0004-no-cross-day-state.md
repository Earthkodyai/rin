# ADR 0004: Every morning stands alone; no streak, no Bond level

- **Status:** accepted, 2026-09-30 (plan decision D22)
- **Supersedes:** the Bond level of D5, Bond and streak in Phase 5, the Bond notes in D15, D18 and [ADR 0002](0002-no-wake-up-check.md) (the emergency hold no longer costs anything)

## Context
Phase 5 was planned as an emotion engine plus a friendship meter: a Bond level that grows with good mornings and drops a little (bounded, recoverable) on emergency holds; a streak of mornings in a row with milestone lines; lines unlocked by Bond; and a 30-day simulation to prove Bond does not swing. The 4.1 script carried about 30 lines for it: streak milestones (2, 3, 5, 7, 14, 30 days), streak kept, streak broken, welcome back after days away, Bond up, and "I couldn't wake you yesterday".

Reviewing the script, the tester said the app only wakes you up, one day at a time. It has no reason to count how many days in a row you got up or to track a relationship level.

This matches ADR 0002 and ADR 0003: the app does its one job (you are awake when the alarm stops) and leaves the rest of the day, and the user, alone.

## Decision
Nothing about the user's mornings carries over to the next day. No Bond level, no streak, no unlockable lines, no "welcome back" and no "yesterday" lines. Rin's mood is a state machine inside one morning (sleepy or cheerful by time → pouty and sulky with snoozes → proud or relieved when the game is won) and resets at every ring.

What stays: rest days and sick days (settings, one line each morning), switching off pouting, and the ring log. The ring log is a reliability tool for Diagnostics and the 14-night run, not something Rin talks about.

## Consequences
- The script drops to 185 lines + R01–R30 = 215 clips, 8,093 characters (≈3.7× fits the 30k-credit paid month).
- Phase 5 shrinks to the one-morning mood machine and the settings page, about 2–3 days instead of 4–5, with no 30-day Bond simulation.
- **No line history is stored either:** 4.2 picks each daily line from a fixed shuffle of its pool indexed by the date, like "Rin picks" already rotates games. A pool of N lines then repeats only every N days (≥ 7 by the checker), with no record of what played before.
- 05e's Bond ethics (floor, small bounded losses, no shaming a broken streak) become moot: there is nothing to lose and nothing to shame. The emergency hold has no penalty.
- Less for the privacy review: no per-day behaviour history.
- Lost: the "gamified relationship" hook that companion apps use for retention. It fits the tester's view (ADR 0002: strict self-control tools and guilt get abandoned), and the one-day design is easier to explain.

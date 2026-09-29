# ADR 0002: No wake-up check; the game ends the alarm

- **Status:** accepted, 2026-09-30 (plan decision D18)
- **Supersedes:** the wake-up check and bed-return parts of [ADR 0001](0001-games-replace-out-of-bed-missions.md) and of D15–D17

## Context
ADR 0001 made the games the way to stop an alarm, and accepted that a game proves the user is awake, not out of bed. A **wake-up check** was to cover the gap: five minutes after dismissal the alarm would ask for one short colour-pad round within 60 s, or ring again, with bed-return signals (phone flat and still, screen off, dark, no steps) logged beside it.

While designing task 3.6 the tester raised a case the design could not handle: a user dismisses the alarm, leaves the phone on the bed and goes to shower, and the phone rings again on an empty bed. Looking into it showed three problems:

1. **The phone cannot tell the two cases apart.** A phone left on the bed and a phone next to someone who fell asleep again give the same signals: flat, still, screen off, no steps. No threshold separates them; any rule either rings on an empty bed or misses the sleeper. (Mattress micro-motion might separate them, but it was never measured.)
2. **Snoozing is what most people do, and a short snooze looks harmless.** Over 3 million nights from 21,000 Sleep Cycle users, more than half of mornings ended with a snooze (2–3 presses, about 11 minutes; Robbins et al., *Scientific Reports* 2025). In a lab study, 30 minutes of snoozing left habitual snoozers' cognition the same or slightly better, with no effect on cortisol, mood or sleepiness (Sundelin et al., *J Sleep Research* 2023).
3. **Strict self-control tools get abandoned.** Research on digital self-control tools names users reacting against their own constraints and dropping the tool as a main problem, and recommends adjustable strength and respect for autonomy (Lyngs, Lukoff et al.). The strictest alarm app on the market, Alarmy, offers its Wake Up Check as an optional Premium feature, not a default.

Softer variants were considered (a "going to shower" button that postpones the check, skipping the check when the phone walked away, a gentle default with a strict opt-in). The tester chose none of them: the games wake the user up enough, and staying up is the user's own responsibility.

## Decision
The alarm ends when the game is passed (or the 3 s emergency hold is used). After that the app does nothing: no check, no re-ring, no sensor watch, no back-to-bed event. Snooze (5 minutes, up to 3 times) stays as the user's own buffer.

## Consequences
- Task 3.6 is dropped. Phase 3 ends with 3.7, the 14 real mornings, which now measure the games only: dismissed by a game within 2 minutes on at least 90% of mornings, and every emergency hold in the log matching a real one.
- The product claim narrows and is stated openly: RinAlarm makes sure you are awake when the alarm stops; it does not try to keep you out of bed.
- Phase 5 charges Bond only for the emergency hold; there is no back-to-bed penalty, so there is nothing to charge the wrong person for.
- Less code on the ring path and no background sensing after dismissal: nothing to explain in a privacy review, and no foreground notification lingering after the alarm.
- `AccelStepDetector` no longer has a job in the app; it stays as the measured result of task 3.1. `PadsRules.shortRound()`, made for the check, was removed.
- If testers later ask for a stricter mode, the 3.6 design (soft 60 s window, then a full ring; a postpone button; skipping when the phone walked away) can come back as an opt-in per alarm, starting with a measurement of mattress micro-motion.

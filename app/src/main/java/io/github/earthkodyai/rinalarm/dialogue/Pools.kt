package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.DefaultMood
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.Miss
import io.github.earthkodyai.rinalarm.mission.MissionType
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Which pool Rin draws from in each moment (script-bible §4, task 4.2). Pure, so every rule is unit-tested. During a
 * game she speaks only after a mistake (D23, the user's pick): a right answer gets no line, so the games keep the
 * timings frozen for their held-out runs.
 */
object Pools {
  /**
   * Her first line when the alarm rings. After a snooze: the `back.*` set of the snooze just taken. Otherwise a late
   * ring says so, a weekend gets the day-off lines, and the rest go by the ring time's mood (sleepy 22:00-05:59).
   *
   * @param snoozes the snoozes already taken this morning; [snoozesLeft] those still allowed.
   */
  fun opening(snoozes: Int, snoozesLeft: Int, late: Boolean, day: DayOfWeek, time: LocalTime): String =
    when {
      snoozes > 0 -> "back." + step(snoozes, snoozes + snoozesLeft)
      late -> "ring.late"
      day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY -> "ring.dayoff"
      DefaultMood.at(time) == Mood.SLEEPY -> "ring.sleepy"
      else -> "ring.cheerful"
    }

  /** The snooze being taken now, with [snoozes] taken before it: the first, the last one allowed, or one between. */
  fun snooze(snoozes: Int, snoozesLeft: Int): String = "snooze." + step(snoozes + 1, snoozes + snoozesLeft)

  /** A cap of 1 makes the only snooze the first (script-bible §4), and its re-ring the `back.first` one. */
  private fun step(n: Int, cap: Int): String =
    when {
      n <= 1 -> "first"
      n >= cap -> "last"
      else -> "again"
    }

  const val EMERGENCY = "emergency"

  /** A rest or sick day's ring (Phase 5): her day-mode line, as the opening one and again after a snooze. */
  fun dayMode(kind: DayModeKind): String = "p5." + kind.stored

  /** "Let's play": the game's own intro lines, drawn together with the shared ones. */
  fun intro(game: MissionType): String =
    when (game) {
      MissionType.PADS -> "game.intro.pads"
      MissionType.CUPS -> "game.intro.cups"
      MissionType.SPEECH -> "game.intro.speech"
    }

  fun padsScold(miss: Miss): String = if (miss == Miss.SLOW) "pads.slow" else "pads.wrong"

  const val CUPS_SCOLD = "cups.wrong"

  /**
   * Repeat after Rin's feedback on a missed try (a right one gets her nod, no line): what to do next, and once out of
   * spoken tries, the move to tapping.
   */
  fun speechMiss(feedback: Feedback, outOfTries: Boolean): String =
    when {
      outOfTries -> "speech.totap"
      feedback == Feedback.NOTHING -> "speech.nothing"
      else -> "speech.missed"
    }

  fun won(clean: Boolean): String = if (clean) "won.clean" else "won.hard"

  /**
   * The closing remark after the win: the meal ahead by the alarm's ring time (breakfast 04-10, lunch 11-15, dinner
   * 16-21, late 22-03), drawn together with the general remarks.
   */
  fun closing(time: LocalTime): String =
    "after.meal." +
      when (time.hour) {
        in 4..10 -> "breakfast"
        in 11..15 -> "lunch"
        in 16..21 -> "dinner"
        else -> "late"
      }

  /** The home screen's hello when the app opens: morning 05-11, afternoon 12-17, evening 18-21, night 22-04. */
  fun appOpened(time: LocalTime): String =
    "app." +
      when (time.hour) {
        in 5..11 -> "morning"
        in 12..17 -> "afternoon"
        in 18..21 -> "evening"
        else -> "night"
      }

  const val ALARM_SET = "app.alarmset"
  const val HEAD_TAP = "tap.head"
}

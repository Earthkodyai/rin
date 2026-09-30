package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.Miss
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.testing.realScript
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolsTest {
  private val weekday = DayOfWeek.TUESDAY
  private val six = LocalTime.of(6, 30)

  @Test
  fun theFirstLine_afterASnooze_isTheBackSetOfThatSnooze() {
    // A cap of 3: first, again, last.
    assertEquals("back.first", Pools.opening(1, 2, late = false, weekday, six))
    assertEquals("back.again", Pools.opening(2, 1, late = false, weekday, six))
    assertEquals("back.last", Pools.opening(3, 0, late = false, weekday, six))
    // A cap of 1: the only snooze is the first (script-bible §4), even with none left.
    assertEquals("back.first", Pools.opening(1, 0, late = false, weekday, six))
    // Back beats late and the weekend.
    assertEquals("back.first", Pools.opening(1, 2, late = true, DayOfWeek.SUNDAY, six))
  }

  @Test
  fun theFirstLine_otherwise_goesLate_thenWeekend_thenTheRingTimesMood() {
    assertEquals("ring.late", Pools.opening(0, 3, late = true, DayOfWeek.SATURDAY, six))
    assertEquals("ring.dayoff", Pools.opening(0, 3, late = false, DayOfWeek.SATURDAY, six))
    assertEquals("ring.dayoff", Pools.opening(0, 3, late = false, DayOfWeek.SUNDAY, LocalTime.of(5, 0)))
    assertEquals("ring.sleepy", Pools.opening(0, 3, late = false, weekday, LocalTime.of(5, 59)))
    assertEquals("ring.sleepy", Pools.opening(0, 3, late = false, weekday, LocalTime.of(22, 0)))
    assertEquals("ring.cheerful", Pools.opening(0, 3, late = false, weekday, LocalTime.of(6, 0)))
  }

  @Test
  fun aSnooze_isTheFirst_theLastAllowed_orOneBetween() {
    assertEquals("snooze.first", Pools.snooze(0, 3))
    assertEquals("snooze.again", Pools.snooze(1, 2))
    assertEquals("snooze.last", Pools.snooze(2, 1))
    assertEquals("snooze.first", Pools.snooze(0, 1)) // a cap of 1
    assertEquals("snooze.last", Pools.snooze(1, 1)) // a cap of 2
  }

  @Test
  fun meals_andHellos_followTheClock() {
    val meal = { h: Int -> Pools.closing(LocalTime.of(h, 0)).removePrefix("after.meal.") }
    assertEquals(listOf("late", "breakfast", "breakfast", "lunch", "lunch", "dinner", "dinner", "late"), listOf(3, 4, 10, 11, 15, 16, 21, 22).map(meal))
    val hello = { h: Int -> Pools.appOpened(LocalTime.of(h, 59)).removePrefix("app.") }
    assertEquals(listOf("night", "morning", "morning", "afternoon", "afternoon", "evening", "evening", "night"), listOf(4, 5, 11, 12, 17, 18, 21, 22).map(hello))
  }

  @Test
  fun aMissedTry_saysWhatToDoNext_andMovesToTappingWhenOutOfTries() {
    assertEquals("speech.nothing", Pools.speechMiss(Feedback.NOTHING, outOfTries = false))
    assertEquals("speech.missed", Pools.speechMiss(Feedback.MISSED, outOfTries = false))
    assertEquals("speech.totap", Pools.speechMiss(Feedback.MISSED, outOfTries = true))
    assertEquals("speech.totap", Pools.speechMiss(Feedback.NOTHING, outOfTries = true))
  }

  @Test
  fun everyPoolTheAppAsksFor_hasLinesInTheScript() {
    val asked =
      listOf(
        Pools.opening(0, 3, false, weekday, six),
        Pools.EMERGENCY,
        Pools.CUPS_SCOLD,
        Pools.ALARM_SET,
        Pools.HEAD_TAP,
        Pools.won(true),
        Pools.won(false),
      ) +
        (1..3).flatMap { n -> listOf(Pools.opening(n, 3 - n, false, weekday, six), Pools.snooze(n - 1, 4 - n)) } +
        listOf(true, false).map { Pools.opening(0, 3, it, DayOfWeek.SATURDAY, LocalTime.of(23, 0)) } +
        Pools.opening(0, 3, false, weekday, LocalTime.of(23, 0)) +
        MissionType.entries.map(Pools::intro) +
        Miss.entries.map(Pools::padsScold) +
        Feedback.entries.flatMap { f -> listOf(true, false).map { Pools.speechMiss(f, it) } } +
        (0..23).flatMap { listOf(Pools.closing(LocalTime.of(it, 0)), Pools.appOpened(LocalTime.of(it, 0))) }
    for (pool in asked.toSet()) assertTrue("$pool has no lines", realScript.pool(pool).isNotEmpty())
  }
}

class ScriptTest {
  @Test
  fun everyLine_hasAKnownMood_andItsGestureIfItNamesOne() {
    val file = File("src/main/assets/dialogue/lines.json").readText()
    val named = Regex("\"gesture\": \"([a-z]+)\"").findAll(file).count()
    assertEquals(named, realScript.lines.count { it.gesture != null })
    assertTrue(realScript.lines.all { it.emotion != null })
    assertTrue(realScript.lines.all { it.pool in realScript.pools })
    assertEquals(realScript.lines.size, realScript.lines.map { it.id }.toSet().size)
  }

  @Test
  fun onTheHomeStrip_pout_andClap_becomeGesturesThatReadFromHeadAndShoulders() {
    assertEquals(Gesture.HUFF, Gesture.POUT.onStrip())
    assertEquals(Gesture.JOY, Gesture.CLAP.onStrip())
    assertEquals(Gesture.WAVE, Gesture.WAVE.onStrip())
  }
}

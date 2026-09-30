package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.mission.MissionPlanner
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.testing.realScript
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phase-4 exit rule "no line again within 7 days" (plan phase-4), checked on a simulated calendar instead of 7 real
 * days: every daily situation, a year and more of days, from the real script.
 */
class LinePickerTest {
  private val start = LocalDate.of(2026, 10, 1)
  private val days = (0L until 800L).map(start::plusDays)

  /** Fails with the pool and the two days when any line comes back within [window] days. */
  private fun assertNoRepeatWithin(window: Int, picks: List<Line>, what: String) {
    val lastDay = mutableMapOf<String, Int>()
    picks.forEachIndexed { day, line ->
      lastDay[line.id]?.let { assertTrue("$what: ${line.id} on day $it and again on day $day", day - it >= window) }
      lastDay[line.id] = day
    }
  }

  private val daily = realScript.pools.filterValues { it.use == PoolUse.DAILY }.keys

  @Test
  fun everyDailyPool_neverRepeatsALineWithin7Days() {
    val picker = LinePicker(realScript)
    for (pool in daily) {
      val picks = days.map { checkNotNull(picker.daily(pool, it)) { pool } }
      assertNoRepeatWithin(7, picks, pool)
    }
  }

  @Test
  fun everyDailyPool_getsToAllOfItsLines_andTheSharedOnes() {
    val picker = LinePicker(realScript)
    for (pool in daily) {
      val said = days.map { picker.daily(pool, it)!!.id }.toSet()
      val mix = realScript.pools.getValue(pool).mix
      val all = (realScript.pool(pool) + mix?.let(realScript::pool).orEmpty()).map { it.id }.toSet()
      assertEquals(pool, all, said)
    }
  }

  @Test
  fun theSharedIntroLines_neverRepeatWithin7Days_whenTheGameChangesEveryDay() {
    // Rin picks rotates the game daily; a phone can also change games at random (a mission not ready, a new choice).
    val picker = LinePicker(realScript)
    val games = listOf(MissionType.PADS, MissionType.CUPS, MissionType.SPEECH)
    val rotating = days.map { picker.daily(Pools.intro(MissionPlanner.rotate(games, it)), it)!! }
    assertNoRepeatWithin(7, rotating, "Rin picks")
    val random = Random(4)
    val shuffled = days.map { picker.daily(Pools.intro(games[random.nextInt(3)]), it)!! }
    assertNoRepeatWithin(7, shuffled, "random games")
  }

  @Test
  fun theClosingRemarks_neverRepeatWithin7Days_whenTheRingTimeMovesAcrossMeals() {
    val picker = LinePicker(realScript)
    val random = Random(9)
    val picks = days.map { picker.daily(Pools.closing(LocalTime.of(random.nextInt(24), 0)), it)!! }
    assertNoRepeatWithin(7, picks, "closing remarks")
  }

  @Test
  fun theDailyOrder_isTheSameOnEveryPhone_andMovesOnEachDay() {
    val a = LinePicker(realScript, Random(1))
    val b = LinePicker(realScript, Random(2))
    for (day in days.take(30)) assertEquals(a.daily("ring.sleepy", day), b.daily("ring.sleepy", day))
    assertNotEquals(a.daily("ring.sleepy", start), a.daily("ring.sleepy", start.plusDays(1)))
  }

  @Test
  fun aSessionPool_emptiesItsBagBeforeALineComesBack_andANewMorningStartsAgain() {
    val picker = LinePicker(realScript, Random(3))
    val size = realScript.pool("pads.wrong").size
    val round1 = List(size) { picker.session("pads.wrong", "m1")!!.id }
    assertEquals(size, round1.toSet().size)
    val next = picker.session("pads.wrong", "m1")!!.id
    assertNotEquals("no repeat across the bag's refill", round1.last(), next)

    // A new morning: a fresh bag (it may start anywhere).
    val round2 = List(size) { picker.session("pads.wrong", "m2")!!.id }
    assertEquals(size, round2.toSet().size)
  }

  @Test
  fun anEventPool_neverSaysTheSameLineTwiceInARow() {
    val picker = LinePicker(realScript, Random(5))
    val picks = List(60) { picker.event("snooze.first")!!.id }
    assertTrue(picks.zipWithNext().none { (a, b) -> a == b })
    assertEquals(realScript.pool("snooze.first").map { it.id }.toSet(), picks.toSet())
  }

  @Test
  fun aPoolWithNoLines_givesNothing() {
    val picker = LinePicker(realScript)
    assertNull(picker.pick("no.such.pool", start, Unit))
  }
}

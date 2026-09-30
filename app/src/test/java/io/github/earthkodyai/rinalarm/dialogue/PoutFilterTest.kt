package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.testing.realLineBook
import io.github.earthkodyai.rinalarm.testing.realScript
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoutFilterTest {
  private val day = LocalDate.of(2026, 10, 1)

  @Test
  fun calm_aHeadTapNeverGetsTheOnePoutyLine_butStillGetsALine() = runTest {
    val book = PoutFilter(realLineBook()) { true }
    repeat(200) {
      val line = assertNotNullLine(book.pick(Pools.HEAD_TAP, day, Unit))
      assertFalse(line.id, line.emotion?.pouty == true)
    }
  }

  @Test
  fun calm_theAllPoutyPools_stayQuiet() = runTest {
    val book = PoutFilter(realLineBook()) { true }
    for (pool in listOf("snooze.again", "snooze.last", "back.again", "back.last", "pads.slow", "pads.wrong", "cups.wrong")) {
      assertNull(pool, book.pick(pool, day, Unit))
    }
  }

  @Test
  fun notCalm_passesEveryLineThrough() = runTest {
    val book = PoutFilter(realLineBook()) { false }
    assertEquals(true, book.pick("cups.wrong", day, Unit)?.emotion?.pouty)
  }

  @Test
  fun theDayModePools_haveLines_andNoneOfThemPout() {
    for (kind in DayModeKind.entries) {
      val lines = realScript.lines.filter { it.pool == Pools.dayMode(kind) }
      assertTrue(kind.name, lines.isNotEmpty())
      assertFalse(kind.name, lines.any { it.emotion?.pouty == true })
    }
  }

  private fun assertNotNullLine(line: Line?): Line = line.also { assertNotNull(it) }!!
}

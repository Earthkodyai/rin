package io.github.earthkodyai.rinalarm.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayModeTest {
  private val setAt = 1_000_000L
  private val mode = DayMode(DayModeKind.REST, setAt)

  @Test
  fun tappedAtNight_stillCoversTheMorningRing() {
    assertTrue(mode.activeAt(setAt + 8 * HOUR))
  }

  @Test
  fun lapsesAfter24Hours() {
    assertTrue(mode.activeAt(setAt + 24 * HOUR - 1))
    assertFalse(mode.activeAt(setAt + 24 * HOUR))
  }

  @Test
  fun aClockSetBackBeforeTheTap_doesNotCountAsActive() {
    assertFalse(mode.activeAt(setAt - 1))
  }

  @Test
  fun storedNames_roundTrip() {
    DayModeKind.entries.forEach { assertTrue(DayModeKind.fromStored(it.stored) == it) }
    assertTrue(DayModeKind.fromStored("nope") == null)
  }

  private companion object {
    const val HOUR = 3_600_000L
  }
}

package io.github.earthkodyai.rinalarm.ui.common

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUntilTest {
  @Test
  fun roundsUpToTheNextMinute() {
    assertEquals(TimeUntil(0, 0, 1), TimeUntil.of(Duration.ofSeconds(30)))
    assertEquals(TimeUntil(0, 0, 1), TimeUntil.of(Duration.ofMillis(1)))
    assertEquals(TimeUntil(0, 0, 2), TimeUntil.of(Duration.ofSeconds(61)))
    assertEquals(TimeUntil(0, 0, 1), TimeUntil.of(Duration.ofMinutes(1)))
  }

  @Test
  fun splitsIntoDaysHoursMinutes() {
    assertEquals(TimeUntil(0, 7, 20), TimeUntil.of(Duration.ofMinutes(440)))
    assertEquals(TimeUntil(1, 0, 0), TimeUntil.of(Duration.ofDays(1)))
    assertEquals(TimeUntil(6, 23, 59), TimeUntil.of(Duration.ofDays(7).minusSeconds(61)))
  }

  @Test
  fun zeroOrNegative_isZero() {
    assertEquals(TimeUntil(0, 0, 0), TimeUntil.of(Duration.ZERO))
    assertEquals(TimeUntil(0, 0, 0), TimeUntil.of(Duration.ofSeconds(-5)))
  }
}

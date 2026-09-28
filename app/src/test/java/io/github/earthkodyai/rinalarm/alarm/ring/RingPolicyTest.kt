package io.github.earthkodyai.rinalarm.alarm.ring

import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPolicyTest {
  @Test
  fun ramp_startsAroundFifteenPercent_andEndsAtFull() {
    assertEquals(0.158f, RingPolicy.rampGain(0, 30), 0.001f)
    assertEquals(1f, RingPolicy.rampGain(30_000, 30), 0f)
    assertEquals(1f, RingPolicy.rampGain(90_000, 30), 0f)
  }

  @Test
  fun ramp_isLinearInDecibels() {
    val halfwayDb = 20 * log10(RingPolicy.rampGain(15_000, 30).toDouble())
    assertEquals(RingPolicy.RAMP_START_DB / 2, halfwayDb, 0.01)
  }

  @Test
  fun ramp_neverGetsQuieter() {
    val gains = (0..31_000 step 200).map { RingPolicy.rampGain(it.toLong(), 30) }
    assertTrue(gains.zipWithNext().all { (a, b) -> b >= a })
  }

  @Test
  fun rampOff_isFullVolumeAtOnce() {
    assertEquals(1f, RingPolicy.rampGain(0, 0), 0f)
  }

  @Test
  fun volumeFloor_raisesAnythingBelowFortyPercent() {
    // Xiaomi 14T alarm stream: max 15, floor ceil(6.0) = 6.
    assertEquals(6, RingPolicy.volumeFloorIndex(current = 0, max = 15))
    assertEquals(6, RingPolicy.volumeFloorIndex(current = 5, max = 15))
    assertNull(RingPolicy.volumeFloorIndex(current = 6, max = 15))
    assertNull(RingPolicy.volumeFloorIndex(current = 15, max = 15))
    // Stock Android: max 7, floor ceil(2.8) = 3.
    assertEquals(3, RingPolicy.volumeFloorIndex(current = 1, max = 7))
  }

  @Test
  fun missionQuiet_lastsThirtySecondsAfterTheLastProgress() {
    assertFalse(RingPolicy.missionQuiet(nowMs = 10_000, lastProgressMs = null))
    assertTrue(RingPolicy.missionQuiet(nowMs = 10_000, lastProgressMs = 10_000))
    assertTrue(RingPolicy.missionQuiet(nowMs = 39_999, lastProgressMs = 10_000))
    assertFalse(RingPolicy.missionQuiet(nowMs = 40_000, lastProgressMs = 10_000))
    // A clock reading before the progress (never on elapsedRealtime, but cheap to guard): not quiet.
    assertFalse(RingPolicy.missionQuiet(nowMs = 9_000, lastProgressMs = 10_000))
  }

  @Test
  fun toneGain_isCappedWhileQuiet_andNeverRaisedByIt() {
    assertEquals(RingPolicy.MISSION_QUIET_GAIN, RingPolicy.toneGain(60_000, 30, quiet = true), 0f)
    assertEquals(1f, RingPolicy.toneGain(60_000, 30, quiet = false), 0f)
    // At any point of the ramp, quiet can only lower the tone.
    for (t in 0L..31_000L step 500) {
      assertTrue(RingPolicy.toneGain(t, 30, quiet = true) <= RingPolicy.toneGain(t, 30, quiet = false))
    }
  }
}

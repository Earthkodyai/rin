package io.github.earthkodyai.rinalarm.alarm.ring

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmBufferTest {
  @Test
  fun appends_growPastTheFirstBlock() {
    val buffer = PcmBuffer(capacity = 3_000_000)
    val chunk = ShortArray(700_000) { (it % 100).toShort() }
    repeat(3) { assertTrue(buffer.append(chunk)) }
    assertEquals(2_100_000, buffer.size)
    assertEquals(chunk[699_999], buffer.trimmed(0)[2_099_999])
  }

  @Test
  fun aFullBuffer_refusesTheChunk_andKeepsWhatItHad() {
    val buffer = PcmBuffer(capacity = 10)
    assertTrue(buffer.append(shortArrayOf(1, 2, 3, 4, 5, 6)))
    assertFalse(buffer.append(shortArrayOf(7, 8, 9, 10, 11)))
    assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6), buffer.trimmed(0))
  }

  @Test
  fun trimmed_dropsTheEncodersPadding_neverBelowNothing() {
    val buffer = PcmBuffer(capacity = 100)
    buffer.append(shortArrayOf(1, 2, 3, 4, 5, 6))
    assertArrayEquals(shortArrayOf(1, 2, 3, 4), buffer.trimmed(2))
    assertEquals(0, buffer.trimmed(50).size)
    assertEquals(6, buffer.trimmed(-3).size)
  }

  @Test
  fun monoToStereo_doublesEverySample() {
    assertArrayEquals(shortArrayOf(1, 1, -2, -2, 3, 3), Pcm.monoToStereo(shortArrayOf(1, -2, 3)))
  }

  @Test
  fun theBeep_isAOneSecondMonoLoop() {
    assertEquals(44_100, TonePlayer.tonePattern().size)
  }
}

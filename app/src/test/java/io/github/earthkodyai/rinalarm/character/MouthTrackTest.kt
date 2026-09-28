package io.github.earthkodyai.rinalarm.character

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthTrackTest {
  @Test
  fun parse_readsWhatToolsVoiceWrites_andRejectsAnythingElse() {
    val track = MouthTrack.parse("""{"v":1,"fps":30,"f":"-0a2a9o4-0"}""")!!
    assertEquals(30, track.fps)
    assertEquals(5 / 30.0, track.seconds, 1e-9)

    assertNull(MouthTrack.parse("""{"v":2,"fps":30,"f":"a2"}""")) // a newer format
    assertNull(MouthTrack.parse("""{"v":1,"fps":30,"f":"a"}""")) // half a frame
    assertNull(MouthTrack.parse("""{"v":1,"fps":30,"f":"x5"}""")) // unknown shape
    assertNull(MouthTrack.parse("""{"v":1,"fps":0,"f":""}"""))
    assertNull(MouthTrack.parse("not json"))
  }

  @Test
  fun fromLoudness_isClosedInSilence_andOpenWhereTheVoiceIs() {
    val rate = 24_000
    val samples = ShortArray(rate) // 1 s: silence, then a 0.4 s tone from 0.3 s, then silence
    for (i in (0.3 * rate).toInt() until (0.7 * rate).toInt()) {
      samples[i] = (12_000 * sin(2 * PI * 220 * i / rate)).toInt().toShort()
    }

    val frames = MouthTrack.fromLoudness(samples, rate).f.chunked(2)

    assertEquals(30, frames.size)
    assertTrue(frames.take(7).all { it == "-0" })
    assertTrue(frames.subList(10, 20).all { it[0] == 'a' && it[1].digitToInt() >= 7 })
    assertTrue(frames.drop(23).all { it == "-0" })
  }

  @Test
  fun fromLoudness_ofSilence_isAClosedMouth() {
    assertTrue(MouthTrack.fromLoudness(ShortArray(8_000), 8_000).f.chunked(2).all { it == "-0" })
  }
}

package io.github.earthkodyai.rinalarm.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskDecoderTest {
  /** Replays a script of 100 ms chunks: at chunk i, an utterance result, or a partial. */
  private class Script(private val results: Map<Int, String>, private val partials: Map<Int, String> = emptyMap(), private val final: String = "{}") :
    Recognizing {
    private var chunk = -1
    var closed = false

    override fun accept(buffer: ShortArray, n: Int): Boolean {
      chunk++
      return chunk in results
    }

    override fun result() = results.getValue(chunk)

    override fun partial() = """{"partial":"${partials[chunk].orEmpty()}"}"""

    override fun final() = final

    override fun close() {
      closed = true
    }
  }

  private fun utterance(vararg words: String, endAt: Double = 1.0) =
    """{"result":[${words.joinToString(",") { """{"conf":1.0,"word":"$it","end":$endAt}""" }}],"text":"${words.joinToString(" ")}"}"""

  private val chunk = ShortArray(1_600)

  /** Feeds chunks until the decoder is done or [max] chunks; returns how many were fed. */
  private fun run(d: VoskDecoder, max: Int = 80): Int {
    repeat(max) { i -> if (d.feed(chunk, chunk.size)) return i + 1 }
    return max
  }

  @Test
  fun aPauseInsideTheSentence_doesNotEndTheTry_theWordsOnBothSidesCount() {
    // Dev d04: "The sun is up, || and so am I" — speech resumes 0.3 s after the first utterance closed.
    val script =
      Script(
        results = mapOf(39 to utterance("the", "sun", "is", "up"), 59 to utterance("and", "so", "am", "i", endAt = 5.2)),
        partials = mapOf(42 to "and", 47 to "and so"),
      )
    val d = VoskDecoder(script)
    val fed = run(d)
    assertEquals(listOf("the", "sun", "is", "up", "and", "so", "am", "i"), d.heard().words.map { it.word })
    assertEquals(60 + 10, fed) // the second utterance, then QUIET_MS of silence
  }

  @Test
  fun enough_endsTheTryAtOnce() {
    val d = VoskDecoder(Script(mapOf(20 to utterance("good", "morning"))), enough = { h -> h.words.size >= 2 })
    assertEquals(21, run(d))
    assertEquals(2, d.heard().words.size)
  }

  @Test
  fun silenceAfterSpeech_endsTheTry_andUnkIsCountedNotHeard() {
    val d = VoskDecoder(Script(mapOf(10 to """{"result":[{"conf":1.0,"word":"my","end":0.6},{"conf":1.0,"word":"[unk]","end":1.0}],"text":"my [unk]"}""")))
    assertEquals(11 + 10, run(d))
    val h = d.heard()
    assertEquals(listOf("my"), h.words.map { it.word })
    assertEquals(1, h.unknown)
  }

  @Test
  fun anUtteranceWithNoWords_doesNotStartTheQuietClock() {
    val d = VoskDecoder(Script(mapOf(10 to """{"text":""}""")))
    assertEquals(80, run(d))
    assertFalse(d.heard().words.isNotEmpty())
  }

  @Test
  fun atTheTimeLimit_whatVoskHasNotClosedYetStillCounts() {
    val script = Script(results = emptyMap(), final = utterance("deep", "breath"))
    val d = VoskDecoder(script)
    run(d, max = 30)
    assertEquals(listOf("deep", "breath"), d.heard().words.map { it.word })
    d.close()
    assertTrue(script.closed)
  }
}

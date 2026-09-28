package io.github.earthkodyai.rinalarm.character

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * How Rin's mouth moves through one voice line (format v1, shared with tools/voice/mouth.mjs and the page's
 * mouth.ts): [fps] frames per second, and in [f] two characters per frame, the shape (a i u e o, `-` closed) and the
 * opening 0..9. Voice-pack clips carry one made at build time; [fromLoudness] makes one on the phone when a clip has
 * none (plan 05a: loudness drives `aa`).
 */
@Serializable
data class MouthTrack(val fps: Int, val f: String, val v: Int = 1) {
  init {
    require(v == 1) { "mouth track v$v" }
    require(fps in 1..120) { "fps $fps" }
    require(FORMAT.matches(f)) { "not a v1 mouth track" }
  }

  val seconds: Double
    get() = f.length / 2.0 / fps

  companion object {
    private val FORMAT = Regex("(?:[aiueo-][0-9])*")
    private val json = Json { ignoreUnknownKeys = true }

    /** A track from its JSON file, or null when the file is not a v1 track. */
    fun parse(text: String): MouthTrack? =
      try {
        json.decodeFromString<MouthTrack>(text)
      } catch (_: SerializationException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      }

    const val FPS = 30
    private const val WINDOW_S = 0.04
    /** Same mapping as tools/voice/mouth.mjs: the clip's loud level (95th percentile) opens fully, 20 dB below closes. */
    private const val RANGE_DB = 20.0
    /** Anything quieter than this is silence, however quiet the whole clip is. */
    private const val FLOOR_DB = -60.0

    /**
     * A loudness-only track (shape `a`) from mono PCM, the way tools/voice/mouth.mjs makes one by default, minus its
     * voicing check (which keeps the mouth low on s, sh and f).
     */
    fun fromLoudness(samples: ShortArray, sampleRate: Int, fps: Int = FPS): MouthTrack {
      val count = kotlin.math.ceil(samples.size.toDouble() / sampleRate * fps).toInt()
      val window = (WINDOW_S * sampleRate).roundToInt()
      val levels =
        DoubleArray(count) { k ->
          val start = (((k + 0.5) / fps) * sampleRate - window / 2.0).roundToInt()
          var sum = 0.0
          for (i in start until start + window) {
            if (i in samples.indices) {
              val v = samples[i] / 32768.0
              sum += v * v
            }
          }
          20 * log10(sqrt(sum / window) + 1e-9)
        }
      val loud = levels.sorted().getOrElse((count * 0.95).toInt()) { -100.0 }
      val open = levels.map { if (it < FLOOR_DB) 0.0 else ((it - (loud - RANGE_DB)) / RANGE_DB).coerceIn(0.0, 1.0) }
      val f = StringBuilder(count * 2)
      for (i in 0 until count) {
        val smooth = 0.25 * open.getOrElse(i - 1) { open[i] } + 0.5 * open[i] + 0.25 * open.getOrElse(i + 1) { open[i] }
        val level = (smooth.pow(0.8) * 9).roundToInt()
        f.append(if (level == 0) "-0" else "a$level")
      }
      return MouthTrack(fps, f.toString())
    }
  }
}

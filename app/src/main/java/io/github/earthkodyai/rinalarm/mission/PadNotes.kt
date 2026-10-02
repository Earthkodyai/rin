package io.github.earthkodyai.rinalarm.mission

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import io.github.earthkodyai.rinalarm.alarm.ring.ALARM_AUDIO
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Plays a pad's note. */
interface PadNotes {
  fun play(pad: Pad)

  fun release()
}

/**
 * One short generated note per pad (plan phase-3: each pad has its own note, so the game can be played by ear and
 * without colour vision). D17's four are a C major arpeggio, C5 E5 G5 C6: far enough apart to tell by ear, above the
 * range phone speakers lose, and clear of the alarm's 880 Hz beeps. The 3×3 board's five (G.2) fill out a C major
 * pentatonic, D5 D6 E6 G6 A6, skipping A5 = 880 Hz: no two pads a semitone apart. USAGE_ALARM, like the tone, so it is heard in silent mode; a
 * practice round (UX.8) plays them on the media stream instead ([attributes]).
 */
class AndroidPadNotes(private val attributes: AudioAttributes = ALARM_AUDIO) : PadNotes {
  /** Built on a pad's first note: a game on the 2×2 board never builds the other five. */
  private val tracks = mutableMapOf<Pad, AudioTrack>()
  private var released = false

  override fun play(pad: Pad) {
    if (released) return
    runCatching {
      val track = tracks.getOrPut(pad) { track(FREQUENCIES.getValue(pad)) }
      if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
      track.reloadStaticData()
      track.play()
    }
  }

  override fun release() {
    if (released) return
    released = true
    // After the longest note, so the winning tap's note is not cut off when the mission stops on it.
    if (tracks.isEmpty()) return
    val built = tracks.values.toList()
    Handler(Looper.getMainLooper()).postDelayed({ built.forEach { runCatching { it.release() } } }, NOTE_MS + 50L)
  }

  private fun track(frequency: Double): AudioTrack {
    val pcm = note(frequency)
    return AudioTrack.Builder()
      .setAudioAttributes(attributes)
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(SAMPLE_RATE)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      )
      .setTransferMode(AudioTrack.MODE_STATIC)
      .setBufferSizeInBytes(pcm.size * 2)
      .build()
      .apply { write(pcm, 0, pcm.size) }
  }

  companion object {
    private const val SAMPLE_RATE = 44_100
    private const val NOTE_MS = 320
    private const val FADE_MS = 12

    val FREQUENCIES: Map<Pad, Double> =
      mapOf(
        Pad.RED to 523.25,
        Pad.BLUE to 659.26,
        Pad.YELLOW to 783.99,
        Pad.GREEN to 1046.50,
        Pad.PURPLE to 587.33,
        Pad.CYAN to 1174.66,
        Pad.WHITE to 1318.51,
        Pad.ORANGE to 1567.98,
        Pad.PINK to 1760.00,
      )

    /** A soft sine with a little second harmonic, faded in and out so it does not click, then decaying. */
    fun note(frequency: Double): ShortArray {
      val n = SAMPLE_RATE * NOTE_MS / 1000
      val fade = SAMPLE_RATE * FADE_MS / 1000
      return ShortArray(n) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        val edge = min(1.0, min(i, n - 1 - i).toDouble() / fade)
        val decay = 1.0 - 0.6 * i / n
        val wave = sin(2 * PI * frequency * t) + 0.25 * sin(4 * PI * frequency * t)
        (wave / 1.25 * edge * decay * Short.MAX_VALUE * 0.7).toInt().toShort()
      }
    }
  }
}

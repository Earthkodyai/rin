package io.github.earthkodyai.rinalarm.mission

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
 * without colour vision). A C major arpeggio, C5 E5 G5 C6: far enough apart to tell by ear, above the range phone
 * speakers lose, and clear of the alarm's 880 Hz beeps. USAGE_ALARM, like the tone, so it is heard in silent mode.
 */
class AndroidPadNotes : PadNotes {
  private var tracksBuilt = false
  private val tracks: Map<Pad, AudioTrack> by lazy { Pad.entries.associateWith { track(FREQUENCIES.getValue(it)) } }
  private var released = false

  override fun play(pad: Pad) {
    if (released) return
    runCatching {
      val track = tracks.getValue(pad)
      if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
      track.reloadStaticData()
      track.play()
    }
  }

  override fun release() {
    if (released) return
    released = true
    // Only if a note ever played: releasing would otherwise build all four tracks just to free them. After the
    // longest note, so the winning tap's note is not cut off when the mission stops on it.
    if (!tracksBuilt) return
    Handler(Looper.getMainLooper()).postDelayed({ tracks.values.forEach { runCatching { it.release() } } }, NOTE_MS + 50L)
  }

  private fun track(frequency: Double): AudioTrack {
    tracksBuilt = true
    val pcm = note(frequency)
    return AudioTrack.Builder()
      .setAudioAttributes(ALARM_AUDIO)
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
      mapOf(Pad.RED to 523.25, Pad.BLUE to 659.26, Pad.YELLOW to 783.99, Pad.GREEN to 1046.50)

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

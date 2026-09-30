package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.mission.Hush
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A ring in progress and the mission RingService planned for it (null plan: plain Dismiss). [dayMode]: a rest or sick
 * day covers this ring, so there is no mission and Rin says her day-mode line.
 */
data class ActiveRing(val request: RingRequest, val mission: MissionPlan.Run?, val dayMode: DayModeKind? = null)

/**
 * The ring on screen right now, shared in-process between RingService (writes the ring) and RingActivity (reads it,
 * and reports mission progress, which RingService turns into a quieter tone).
 */
@Singleton
class RingState @Inject constructor() {
  private val current = MutableStateFlow<ActiveRing?>(null)
  val active: StateFlow<ActiveRing?> = current.asStateFlow()

  /** SystemClock.elapsedRealtime() of the last mission progress in this ring, or null before any. */
  @Volatile var lastProgressAt: Long? = null
    private set

  private val hushState = MutableStateFlow(Hush.NONE)
  /** What the game on screen needs from the tone right now (Repeat after Rin: Rin speaking, the mic open). */
  val hush: StateFlow<Hush> = hushState.asStateFlow()

  internal fun set(ring: ActiveRing?) {
    lastProgressAt = null
    hushState.value = Hush.NONE
    current.value = ring
  }

  fun setHush(hush: Hush) {
    hushState.value = if (current.value == null) Hush.NONE else hush
  }

  fun reportProgress(atElapsedMs: Long) {
    if (current.value != null) lastProgressAt = atElapsedMs
  }
}

val ALARM_AUDIO: AudioAttributes =
  AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * A generated beep pattern on USAGE_ALARM. No media file, so it plays before the first unlock after a reboot; it stays
 * the fallback once Rin's voice pack arrives (Phase 4). USAGE_ALARM is also what keeps it audible under Android 17's
 * background audio limits.
 */
class TonePlayer {
  private val pcm = tonePattern()
  private val track: AudioTrack =
    AudioTrack.Builder()
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
      .apply {
        write(pcm, 0, pcm.size)
        setLoopPoints(0, pcm.size, -1)
      }

  fun setGain(gain: Float) {
    track.setVolume(gain)
  }

  fun play() = track.play()

  fun pause() = track.pause()

  fun release() {
    runCatching { track.stop() }
    track.release()
  }

  private companion object {
    const val SAMPLE_RATE = 44_100

    /** A 1 s loop: two 880 Hz beeps, then a pause. Same pattern as the S1 spike. */
    fun tonePattern(): ShortArray {
      val out = ShortArray(SAMPLE_RATE)
      fun beep(fromMs: Int, toMs: Int) {
        for (i in SAMPLE_RATE * fromMs / 1000 until SAMPLE_RATE * toMs / 1000) {
          out[i] = (sin(2 * PI * 880 * i / SAMPLE_RATE) * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
      }
      beep(0, 200)
      beep(300, 500)
      return out
    }
  }
}

/** Repeating vibration marked as an alarm, so it keeps going in silent mode and through a call. */
class AlarmVibrator(context: Context) {
  private val vibrator: Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
      @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

  fun start() {
    val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 1)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    } else {
      @Suppress("DEPRECATION") vibrator.vibrate(effect, ALARM_AUDIO)
    }
  }

  fun stop() = vibrator.cancel()
}

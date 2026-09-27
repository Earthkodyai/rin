package io.github.earthkodyai.rinalarm.alarm.ring

import java.time.Duration
import kotlin.math.ceil
import kotlin.math.pow

/** Numbers that shape a ring, kept free of Android types so RingPolicyTest can pin them. */
object RingPolicy {
  /** A ring nobody answers stops by itself, so a phone left at home does not ring all day. */
  val AUTO_STOP: Duration = Duration.ofMinutes(15)

  /** Loudness the ramp starts at, relative to the alarm stream volume. -16 dB is roughly 15% amplitude. */
  const val RAMP_START_DB = -16.0

  /** While ringing, the alarm stream is raised to at least this share of its maximum (user decision, 1.2). */
  const val VOLUME_FLOOR = 0.4

  /**
   * Track gain (0..1) at [elapsedMillis] into a ramp of [rampSeconds]. The ramp is linear in decibels, which the ear
   * hears as a steady rise; a linear amplitude ramp would sound loud almost at once and then barely change.
   */
  fun rampGain(elapsedMillis: Long, rampSeconds: Int): Float {
    if (rampSeconds <= 0) return 1f
    val progress = (elapsedMillis.toDouble() / (rampSeconds * 1000.0)).coerceIn(0.0, 1.0)
    val db = RAMP_START_DB * (1 - progress)
    return 10.0.pow(db / 20).toFloat()
  }

  /**
   * The alarm stream index to raise to before ringing, or null when [current] is already at or above the floor.
   * [max] is AudioManager.getStreamMaxVolume(STREAM_ALARM).
   */
  fun volumeFloorIndex(current: Int, max: Int): Int? {
    val floor = ceil(max * VOLUME_FLOOR).toInt().coerceAtMost(max)
    return if (current < floor) floor else null
  }
}

package io.github.earthkodyai.rinalarm.mission

import kotlin.math.sqrt

/**
 * Counts walking steps from the accelerometer and gyroscope (task 3.1, docs/spikes/3.1-walk-vs-shake.md).
 *
 * Android's step sensors were no use for the walk mission on the Xiaomi 14T: they counted 0 steps for a walk with the
 * phone held in front (as on the ring screen) and 31–46 for 20 s of shaking. This detector is the rule picked on dev
 * and frozen before held-out (tools/steplab/rule.json), where it counted 36–38 steps per walk and 3–6 for hard
 * shaking. A gentle bob in walking rhythm still counts: motion alone cannot tell it from walking.
 *
 * Online and causal; feed events in time order from one thread. Must count exactly like tools/steplab/tune.py
 * (AccelStepDetectorParityTest).
 *
 * A step is a local maximum of |a| (smoothed over [SMOOTH] samples) that is above 1 g, at least [MIN_GAP_MS] after
 * the last step, rises [LO]..[HI] m/s² above the lowest smoothed value in the [TROUGH_MS] before it, while the
 * gyroscope reads at most [GYRO_MAX] rad/s (shaking twists the phone; walking with it held in front barely does).
 */
class AccelStepDetector {
  private val raw = ArrayDeque<Double>()
  private val times = ArrayDeque<Double>()
  private val smoothed = ArrayDeque<Double>()
  private var gyro = 0.0
  private var lastStep = Double.NEGATIVE_INFINITY

  var steps = 0
    private set

  fun gyroscope(x: Double, y: Double, z: Double) {
    gyro = magnitude(x, y, z)
  }

  /** Feeds one accelerometer sample at [tMs] (any monotonic clock). Returns true when it completed a step. */
  fun accelerometer(tMs: Double, x: Double, y: Double, z: Double): Boolean {
    raw.addLast(magnitude(x, y, z))
    if (raw.size > SMOOTH) raw.removeFirst()
    times.addLast(tMs)
    smoothed.addLast(raw.sum() / raw.size)
    while (times.first() < tMs - TROUGH_MS - HISTORY_MARGIN_MS) {
      times.removeFirst()
      smoothed.removeFirst()
    }
    val n = smoothed.size
    if (n < 3) return false
    val peak = smoothed[n - 2]
    val peakAt = times[n - 2]
    if (!(peak > smoothed[n - 3] && peak >= smoothed[n - 1])) return false
    if (peak <= GRAVITY || peakAt - lastStep < MIN_GAP_MS) return false
    var trough = Double.MAX_VALUE
    for (i in 0 until n - 1) if (times[i] >= peakAt - TROUGH_MS) trough = minOf(trough, smoothed[i])
    val rise = peak - trough
    if (rise < LO || rise > HI || gyro > GYRO_MAX) return false
    steps++
    lastStep = peakAt
    return true
  }

  private fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

  companion object {
    // tools/steplab/rule.json (frozen 7b24b2b). Change only with a new dev pick and held-out run.
    const val LO = 0.6
    const val HI = 10.0
    const val GYRO_MAX = 2.0
    const val SMOOTH = 5
    const val MIN_GAP_MS = 300.0
    const val TROUGH_MS = 400.0
    private const val HISTORY_MARGIN_MS = 100.0
    private const val GRAVITY = 9.81
  }
}

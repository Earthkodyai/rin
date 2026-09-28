package io.github.earthkodyai.rinalarm.mission

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AccelStepDetectorTest {
  /**
   * 20 s at 50 Hz of |a| bouncing around 1 g at [hz] with [amplitude] m/s² (vertical), while the gyroscope reads
   * [gyro] rad/s. Returns the steps counted.
   */
  private fun synthetic(hz: Double, amplitude: Double, gyro: Double): Int {
    val detector = AccelStepDetector()
    for (i in 0 until 1000) {
      val t = i * 20.0
      detector.gyroscope(gyro, 0.0, 0.0)
      detector.accelerometer(t, 0.0, 0.0, 9.81 + amplitude * sin(2 * PI * hz * t / 1000))
    }
    return detector.steps
  }

  @Test
  fun aSteadyWalk_countsOneStepPerBounce() {
    // 1.6 steps/s for 20 s = 32 bounces; the first is spent filling the smoothing window.
    val steps = synthetic(hz = 1.6, amplitude = 1.5, gyro = 0.8)
    assertTrue("steps=$steps", steps in 30..32)
  }

  @Test
  fun twistingThePhone_blocksSteps() {
    assertEquals(0, synthetic(hz = 1.6, amplitude = 1.5, gyro = 5.0))
  }

  @Test
  fun violentMotion_isNotAStep() {
    assertEquals(0, synthetic(hz = 1.6, amplitude = 20.0, gyro = 0.8))
  }

  @Test
  fun aPhoneLyingStill_countsNothing() {
    assertEquals(0, synthetic(hz = 1.6, amplitude = 0.0, gyro = 0.0))
  }

  @Test
  fun fastJitter_isLimitedByTheMinimumGap() {
    // 5 Hz would be 100 bounces; steps must be >= 300 ms apart, so at most ~67.
    assertTrue(synthetic(hz = 5.0, amplitude = 1.5, gyro = 0.8) <= 67)
  }

  /**
   * The Kotlin port must count exactly like tools/steplab/tune.py, the reference the rule was picked and frozen with.
   * Runs only where the step-lab recordings exist (git-ignored, the developer's PC): `python tools/steplab/tune.py
   * counts tools/steplab/data > tools/steplab/data/counts.json`.
   */
  @Test
  fun matchesTheReferenceImplementation_onTheRecordedTrials() {
    val data = File("../tools/steplab/data")
    val expected = File(data, "counts.json")
    assumeTrue("no step-lab recordings here", expected.isFile)
    val reference =
      Regex(""""([^"]+\.csv)":\s*(\d+)""").findAll(expected.readText()).associate {
        it.groupValues[1] to it.groupValues[2].toInt()
      }
    assertTrue(reference.isNotEmpty())
    for ((name, steps) in reference) assertEquals(name, steps, replay(File(data, name)))
  }

  /** tune.py count(): accelerometer and gyroscope rows in time order (stable), up to 20 s. */
  private fun replay(file: File): Int {
    val rows =
      file.readLines().drop(1).mapNotNull { line ->
        val f = line.split(',')
        if (f[1] == "a" || f[1] == "g") Row(f[0].toDouble(), f[1], f[2].toDouble(), f[3].toDouble(), f[4].toDouble())
        else null
      }
    val detector = AccelStepDetector()
    for (r in rows.sortedBy { it.t }) {
      if (r.t > 20_000.0) break
      if (r.type == "a") detector.accelerometer(r.t, r.x, r.y, r.z) else detector.gyroscope(r.x, r.y, r.z)
    }
    return detector.steps
  }

  private data class Row(val t: Double, val type: String, val x: Double, val y: Double, val z: Double)
}

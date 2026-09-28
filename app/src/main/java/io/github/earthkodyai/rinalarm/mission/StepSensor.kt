package io.github.earthkodyai.rinalarm.mission

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Counts steps while started; [onStep] gets the running total. Returns false from [start] when it cannot count. */
interface StepSource {
  fun start(onStep: (Int) -> Unit): Boolean

  fun stop()
}

/**
 * [AccelStepDetector] fed by the accelerometer and gyroscope at ~50 Hz. Android's step sensors are not used: on the
 * 14T they missed hand-held walking and counted shaking (docs/spikes/3.1-walk-vs-shake.md). Needs no permission.
 */
class StepSensor(private val sensors: SensorManager?) : StepSource, SensorEventListener {
  private val detector = AccelStepDetector()
  private var onStep: ((Int) -> Unit)? = null

  override fun start(onStep: (Int) -> Unit): Boolean {
    if (this.onStep != null) return true
    val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyro = sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    // Both or nothing: without the gyroscope, shaking would count as walking.
    val listening =
      accel != null &&
        gyro != null &&
        sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME) &&
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
    if (!listening) {
      sensors?.unregisterListener(this)
      return false
    }
    this.onStep = onStep
    return true
  }

  override fun stop() {
    if (onStep == null) return
    onStep = null
    sensors?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    val callback = onStep ?: return
    val v = event.values
    when (event.sensor.type) {
      Sensor.TYPE_GYROSCOPE -> detector.gyroscope(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
      Sensor.TYPE_ACCELEROMETER ->
        if (detector.accelerometer(event.timestamp / 1_000_000.0, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())) {
          callback(detector.steps)
        }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}

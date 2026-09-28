package io.github.earthkodyai.rinalarm.mission

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Walk [target] steps (user decision D15: 30), counted by [AccelStepDetector] from the accelerometer and gyroscope
 * at ~50 Hz. Android's step sensors are not used: on the 14T they missed hand-held walking and counted shaking
 * (docs/spikes/3.1-walk-vs-shake.md). No runtime permission is needed. A walking-rhythm bob still counts: the mission
 * adds friction, it is not anti-cheat (plan 05c).
 */
class WalkMission(private val sensors: SensorManager, private val target: Int) : Mission, SensorEventListener {
  override val type = MissionType.WALK
  private val detector = AccelStepDetector()
  private val state = MutableStateFlow(MissionProgress(0, target))
  override val progress: StateFlow<MissionProgress> = state.asStateFlow()
  private var listening = false

  override fun start() {
    if (listening || state.value.state != MissionState.RUNNING) return
    val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    // Both, or the mission fails over to a plain Dismiss: without the gyroscope, shaking would pass.
    listening =
      accel != null &&
        gyro != null &&
        sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME) &&
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
    if (!listening) {
      sensors.unregisterListener(this)
      state.value = state.value.copy(state = MissionState.FAILED)
    }
  }

  override fun stop() {
    if (!listening) return
    listening = false
    sensors.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (state.value.state != MissionState.RUNNING) return
    val v = event.values
    when (event.sensor.type) {
      Sensor.TYPE_GYROSCOPE -> detector.gyroscope(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
      Sensor.TYPE_ACCELEROMETER -> {
        val stepped = detector.accelerometer(event.timestamp / 1_000_000.0, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
        if (!stepped) return
        val done = detector.steps.coerceAtMost(target)
        state.value = MissionProgress(done, target, if (done >= target) MissionState.PASSED else MissionState.RUNNING)
        if (done >= target) stop()
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}

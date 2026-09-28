package io.github.earthkodyai.rinalarm.mission

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Which missions can run on this phone right now. Read fresh each time: permissions change in Settings. */
fun interface MissionReadiness {
  fun check(): Map<MissionType, Readiness>
}

class AndroidMissionReadiness @Inject constructor(@ApplicationContext private val context: Context) : MissionReadiness {
  override fun check(): Map<MissionType, Readiness> = MissionType.entries.associateWith(::readiness)

  private fun readiness(type: MissionType): Readiness =
    when (type) {
      // AccelStepDetector needs both: without the gyroscope, shaking would count as walking.
      MissionType.WALK -> {
        val sensors = context.getSystemService(SensorManager::class.java)
        val present =
          sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        if (present) Readiness.READY else Readiness.NO_SENSOR
      }
    }

  companion object {
    /**
     * The runtime permission a mission asks for when the user picks it (D15: at setup, never at install), or null
     * when it needs none. Walking needs none since it stopped using Android's step sensors (3.1 measurement).
     */
    fun permissionFor(type: MissionType): String? =
      when (type) {
        MissionType.WALK -> null
      }
  }
}

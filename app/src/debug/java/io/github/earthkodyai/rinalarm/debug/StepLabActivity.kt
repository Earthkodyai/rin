package io.github.earthkodyai.rinalarm.debug

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.io.File
import java.io.PrintWriter

/**
 * Debug builds only: the walk-versus-shake measurement for task 3.1 (docs/spikes/3.1-walk-vs-shake.md).
 *
 * adb shell am start -n io.github.earthkodyai.rinalarm/.debug.StepLabActivity --es trial dev-walk-1
 *
 * One Start per launch. For [TRIAL_MS] it writes every step-detector and step-counter event plus the accelerometer
 * and gyroscope at ~50 Hz to files/steplab/<trial>.csv (motion only, app-private, pulled with run-as). The screen
 * never shows a count, so trials stay blind; scoring happens on the PC (tools/steplab).
 */
class StepLabActivity : ComponentActivity(), SensorEventListener {
  private var phase by mutableStateOf(Phase.READY)
  private var out: PrintWriter? = null
  private var startedAt = 0L
  private var rows = 0

  private enum class Phase {
    READY,
    RECORDING,
    SAVED,
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val trial = intent.getStringExtra("trial")?.filter { it.isLetterOrDigit() || it == '-' }.orEmpty().ifBlank { "trial" }
    setContent {
      RinAlarmTheme {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(trial, style = MaterialTheme.typography.headlineMedium)
            when (phase) {
              Phase.READY ->
                // Tap-only, and gone once pressed: a second press cannot restart or overwrite the trial.
                Button(onClick = { start(trial) }, Modifier.fillMaxWidth().height(120.dp)) {
                  Text("Start (20 s)", style = MaterialTheme.typography.headlineSmall)
                }
              Phase.RECORDING -> Text("Recording… keep going until it says Saved", style = MaterialTheme.typography.titleLarge)
              Phase.SAVED -> Text("Saved. Wait for the next trial.", style = MaterialTheme.typography.titleLarge)
            }
          }
        }
      }
    }
  }

  private fun start(trial: String) {
    if (phase != Phase.READY) return
    val dir = File(filesDir, "steplab").apply { mkdirs() }
    // Never overwrite an earlier trial: a repeated label gets a suffix.
    var file = File(dir, "$trial.csv")
    var n = 2
    while (file.exists()) file = File(dir, "$trial-${n++}.csv")
    out = PrintWriter(file.bufferedWriter()).apply { println("t_ms,type,x,y,z") }
    startedAt = SystemClock.elapsedRealtimeNanos()
    val sensors = getSystemService(SensorManager::class.java)
    listOf(Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER).forEach { type ->
      sensors.getDefaultSensor(type)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, 0) }
    }
    listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
      sensors.getDefaultSensor(type)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    phase = Phase.RECORDING
    window.decorView.postDelayed({ finishTrial(file) }, TRIAL_MS)
    Log.i(TAG, "start ${file.name}")
  }

  override fun onSensorChanged(event: SensorEvent) {
    val writer = out ?: return
    val tMs = (event.timestamp - startedAt) / 1_000_000.0
    val type =
      when (event.sensor.type) {
        Sensor.TYPE_STEP_DETECTOR -> "d"
        Sensor.TYPE_STEP_COUNTER -> "c"
        Sensor.TYPE_ACCELEROMETER -> "a"
        Sensor.TYPE_GYROSCOPE -> "g"
        else -> return
      }
    val v = event.values
    writer.println(
      "%.1f,%s,%.4f,%.4f,%.4f".format(java.util.Locale.ROOT, tMs, type, v[0], v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f })
    )
    rows++
  }

  private fun finishTrial(file: File) {
    if (phase != Phase.RECORDING) return
    getSystemService(SensorManager::class.java).unregisterListener(this)
    out?.close()
    out = null
    phase = Phase.SAVED
    Log.i(TAG, "saved ${file.name} rows=$rows")
  }

  override fun onDestroy() {
    getSystemService(SensorManager::class.java).unregisterListener(this)
    out?.close()
    super.onDestroy()
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

  private companion object {
    const val TAG = "RinStepLab"
    const val TRIAL_MS = 20_000L
  }
}

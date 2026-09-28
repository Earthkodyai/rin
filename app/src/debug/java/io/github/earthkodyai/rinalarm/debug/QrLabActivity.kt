package io.github.earthkodyai.rinalarm.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
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
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.earthkodyai.rinalarm.data.StickerStore
import io.github.earthkodyai.rinalarm.mission.QrScanner
import io.github.earthkodyai.rinalarm.mission.SeenCode
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.io.File
import java.io.PrintWriter
import java.util.Locale

/**
 * Debug builds only: the size-bar measurement for task 3.2 (docs/spikes/3.2-qr.md).
 *
 * adb shell am start -f 0x10008000 -n io.github.earthkodyai.rinalarm/.debug.QrLabActivity --es trial dev-close-1
 *
 * One Start per launch. For [TRIAL_MS] the camera runs as on the ring screen (1x, auto light) but never passes; every
 * frame writes one row to files/qrlab/<trial>.csv: time, the registered sticker's size (or empty), the largest other
 * code's size, and the flashlight. Sizes only, never a code's content or an image. The screen shows no numbers, so
 * trials stay blind; scoring happens on the PC (tools/qrlab).
 */
class QrLabActivity : ComponentActivity() {
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface Deps {
    fun stickers(): StickerStore
  }

  private var phase by mutableStateOf(Phase.READY)
  private var out: PrintWriter? = null
  private var startedAt = 0L
  /** Set on the main thread, read on the analysis thread. */
  @Volatile private var torch = "off"

  private enum class Phase {
    READY,
    RECORDING,
    SAVED,
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val trial = intent.getStringExtra("trial")?.filter { it.isLetterOrDigit() || it == '-' }.orEmpty().ifBlank { "trial" }
    val sticker = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).stickers().current()
    val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    setContent {
      RinAlarmTheme {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(trial, style = MaterialTheme.typography.headlineMedium)
            when {
              sticker == null || !camera -> Text("Set up the QR sticker first (it asks for the camera).")
              phase == Phase.READY ->
                // Tap-only, and gone once pressed: a second press cannot restart or overwrite the trial.
                Button(onClick = { start(trial) }, Modifier.fillMaxWidth().height(120.dp)) {
                  Text("Start (${TRIAL_MS / 1000} s)", style = MaterialTheme.typography.headlineSmall)
                }
              phase == Phase.RECORDING -> {
                Text("Aim at the sticker until it says Saved", style = MaterialTheme.typography.titleLarge)
                QrScanner(
                  onCodes = { codes -> record(codes, sticker = { sticker.matches(it.content) }) },
                  modifier = Modifier.fillMaxWidth().height(360.dp),
                  onTorch = { on, auto -> torch = if (!on) "off" else if (auto) "auto" else "manual" },
                )
              }
              else -> Text("Saved. Wait for the next trial.", style = MaterialTheme.typography.titleLarge)
            }
          }
        }
      }
    }
  }

  private fun start(trial: String) {
    val dir = File(filesDir, "qrlab").apply { mkdirs() }
    out = PrintWriter(File(dir, "$trial.csv")).apply { println("t_ms,sticker_fraction,other_max,torch") }
    startedAt = SystemClock.elapsedRealtime()
    phase = Phase.RECORDING
    window.decorView.postDelayed({ stop() }, TRIAL_MS)
  }

  /** From the camera's analysis thread. */
  @Synchronized
  private fun record(codes: List<SeenCode>, sticker: (SeenCode) -> Boolean) {
    val writer = out ?: return
    val mine = codes.filter(sticker).maxOfOrNull { it.fraction }
    val other = codes.filterNot(sticker).maxOfOrNull { it.fraction }
    writer.println(
      listOf(SystemClock.elapsedRealtime() - startedAt, mine.fmt(), other.fmt(), torch).joinToString(",")
    )
  }

  @Synchronized
  private fun stop() {
    out?.close()
    out = null
    phase = Phase.SAVED
  }

  private fun Float?.fmt() = this?.let { "%.4f".format(Locale.ROOT, it) } ?: ""

  private companion object {
    const val TRIAL_MS = 10_000L
  }
}

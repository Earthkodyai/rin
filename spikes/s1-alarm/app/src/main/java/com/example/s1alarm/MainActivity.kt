package com.example.s1alarm

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.s1alarm.theme.S1AlarmTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
  private var tick by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleAdbExtras(intent)
    enableEdgeToEdge()
    setContent {
      S1AlarmTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleAdbExtras(intent)
  }

  override fun onResume() {
    super.onResume()
    tick++
  }

  /**
   * Debug-only test hook:
   * adb shell am start -n dev.rinalarm.spike.s1/com.example.s1alarm.MainActivity --ei in_sec 120 --es label doze
   */
  private fun handleAdbExtras(i: Intent) {
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable) return
    if (i.getBooleanExtra("cancel_all", false)) {
      AlarmStore.all(this).forEach { Scheduler.cancel(this, it.id) }
      RingLog.log(this, "cancel_all", null)
      i.removeExtra("cancel_all")
    }
    if (!i.hasExtra("in_sec")) return
    schedule(i.getIntExtra("in_sec", 60), i.getStringExtra("label") ?: "adb", i.getIntExtra("ring_sec", 60))
    i.removeExtra("in_sec")
  }

  private fun schedule(inSec: Int, label: String, ringSec: Int = 60) {
    val now = System.currentTimeMillis()
    val id = ((now / 1000) % 1_000_000).toInt()
    Scheduler.schedule(this, Alarm(id, now + inSec * 1000L, label, ringSec))
    tick++
  }

  @Composable
  private fun Screen() {
    tick // read so the screen recomposes on resume / after scheduling
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    Column(
      Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("S1 alarm spike", style = MaterialTheme.typography.headlineSmall)
      Text("${Build.MANUFACTURER} ${Build.MODEL} · SDK ${Build.VERSION.SDK_INT}")
      Text(deviceState(this@MainActivity).replace(' ', '\n'), fontFamily = FontFamily.Monospace, fontSize = 12.sp)

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
          if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }) { Text("Notif perm") }
        OutlinedButton(onClick = {
          if (Build.VERSION.SDK_INT >= 34) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
          }
        }) { Text("Full-screen perm") }
        OutlinedButton(onClick = {
          startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }) { Text("App settings") }
      }

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (min in listOf(1, 2, 5, 10)) {
          Button(onClick = { schedule(min * 60, "ui+${min}m") }) { Text("+$min min") }
        }
        OutlinedButton(onClick = {
          AlarmStore.all(this@MainActivity).forEach { Scheduler.cancel(this@MainActivity, it.id) }
          tick++
        }) { Text("Cancel all") }
      }

      Text("Pending", style = MaterialTheme.typography.titleMedium)
      for (a in AlarmStore.all(this@MainActivity)) {
        Text("#${a.id} ${a.label} @ ${fmt.format(Date(a.triggerAt))}", fontFamily = FontFamily.Monospace)
      }
      Text("Ring log (newest first)", style = MaterialTheme.typography.titleMedium)
      for (line in RingLog.tail(this@MainActivity, 30)) {
        Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
      }
    }
  }
}

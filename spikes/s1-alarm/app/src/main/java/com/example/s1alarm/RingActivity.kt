package com.example.s1alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.s1alarm.theme.S1AlarmTheme

class RingActivity : ComponentActivity() {
  private val finisher = object : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = finish()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    val label = intent.getStringExtra("label") ?: ""
    RingLog.log(this, "screen_shown", null, "label=$label locked=$locked")
    ContextCompat.registerReceiver(
      this, finisher, IntentFilter(ACTION_FINISH), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    setContent {
      S1AlarmTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text("S1 ALARM", style = MaterialTheme.typography.displayMedium)
            Text(label)
            Button(
              onClick = {
                startService(RingService.stopIntent(this@RingActivity, "dismiss_screen"))
                finish()
              },
              modifier = Modifier.width(240.dp).height(96.dp),
            ) { Text("Dismiss", style = MaterialTheme.typography.headlineMedium) }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    unregisterReceiver(finisher)
    super.onDestroy()
  }

  companion object {
    const val ACTION_FINISH = "com.example.s1alarm.FINISH_RING"
  }
}

package io.github.earthkodyai.rinalarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.alarm.ring.RingActivity
import io.github.earthkodyai.rinalarm.alarm.ring.RingState
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var engine: AlarmEngine
  @Inject @AppScope lateinit var appScope: CoroutineScope
  @Inject lateinit var ringState: RingState

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Safety net for anything the broadcasts missed (e.g. HyperOS blocking BOOT_COMPLETED). Cheap when all is well.
    if (savedInstanceState == null) appScope.launch { engine.reconcile("app_open") }
    // A ring must always be stoppable. With notifications off, Android hides the ring notification and blocks the
    // full-screen launch; opening the app from the launcher then brings up the ring screen instead.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        ringState.active.collect { if (it != null) startActivity(Intent(this@MainActivity, RingActivity::class.java)) }
      }
    }

    enableEdgeToEdge()
    setContent {
      RinAlarmTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}

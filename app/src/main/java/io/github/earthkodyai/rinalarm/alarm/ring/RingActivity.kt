package io.github.earthkodyai.rinalarm.alarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * Full-screen ring UI over the lock screen (showWhenLocked + turnScreenOn in the manifest). A plain placeholder: Rin
 * arrives in Phase 2 and missions in Phase 3. It only mirrors RingState and sends taps to RingService, so the ring
 * keeps going if this screen is closed or never shows (HyperOS full-screen permission off).
 */
@AndroidEntryPoint
class RingActivity : ComponentActivity() {
  @Inject lateinit var ringState: RingState

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
      RinAlarmTheme {
        val active by ringState.active.collectAsStateWithLifecycle()
        LaunchedEffect(active) { if (active == null) finish() }
        active?.let { request ->
          RingScreen(
            request = request,
            onSnooze = { startService(RingService.snoozeIntent(this)) },
            onDismiss = { startService(RingService.dismissIntent(this, "screen")) },
          )
        }
      }
    }
  }
}

@Composable
internal fun RingScreen(request: RingRequest, onSnooze: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
    Column(
      Modifier.safeDrawingPadding().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        request.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        style = MaterialTheme.typography.displayLarge,
      )
      Text(
        request.label.ifBlank { stringResource(R.string.ring_default_label) },
        style = MaterialTheme.typography.headlineSmall,
      )
      if (request.late) {
        Text(stringResource(R.string.ring_late_explained), style = MaterialTheme.typography.bodyMedium)
      }
      Spacer(Modifier.height(48.dp))
      // Big, far-apart targets: the user is half asleep.
      if (request.snoozesLeft > 0) {
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(72.dp)) {
          Text(
            pluralStringResource(
              R.plurals.ring_snooze_left,
              request.snoozesLeft,
              request.options.snoozeMinutes,
              request.snoozesLeft,
            ),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Spacer(Modifier.height(32.dp))
      }
      Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Text(stringResource(R.string.ring_dismiss), style = MaterialTheme.typography.headlineSmall)
      }
    }
  }
}

@Preview
@Composable
private fun RingScreenPreview() {
  RinAlarmTheme {
    RingScreen(
      RingRequest(1, LocalTime.of(7, 0), "Gym", null, 1, 2, late = false, RingOptions()),
      onSnooze = {},
      onDismiss = {},
    )
  }
}

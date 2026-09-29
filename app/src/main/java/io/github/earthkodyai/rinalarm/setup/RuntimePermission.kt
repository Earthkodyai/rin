package io.github.earthkodyai.rinalarm.setup

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri

/**
 * Asks for a runtime [permission] (the mic for Repeat after Rin, from Diagnostics). The first taps show Android's
 * dialog; once Android stops showing it (denied twice, or "don't ask again") the app's info page opens instead.
 */
@Composable
fun rememberRuntimePermissionAction(permission: String, onResult: () -> Unit): () -> Unit {
  val activity = LocalActivity.current
  var blocked by rememberSaveable(permission) { mutableStateOf(false) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
        blocked = true
      }
      onResult()
    }
  return {
    if (!blocked) {
      launcher.launch(permission)
    } else if (activity != null) {
      runCatching {
        activity.startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${activity.packageName}".toUri())
        )
      }
    }
  }
}

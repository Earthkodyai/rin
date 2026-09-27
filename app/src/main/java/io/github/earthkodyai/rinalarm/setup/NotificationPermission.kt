package io.github.earthkodyai.rinalarm.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Turning notifications on. On Android 13+ the first taps show the system dialog; once Android stops showing it
 * (denied twice, or "don't ask again") the button opens the app's notification settings instead. The same happens
 * when the permission is granted but the app's notification switch is off, which HyperOS allows.
 */
class NotificationPermissionAction(
  /** True once the dialog can no longer be shown, so the button should say "Open settings". */
  val opensSettings: Boolean,
  val run: () -> Unit,
)

@Composable
fun rememberNotificationPermissionAction(xiaomiFamily: Boolean, onResult: () -> Unit): NotificationPermissionAction {
  val context = LocalContext.current
  val activity = LocalActivity.current
  var blocked by rememberSaveable { mutableStateOf(false) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          !granted &&
          activity != null &&
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
      ) {
        blocked = true
      }
      onResult()
    }
  val canAsk =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED &&
      !blocked
  return NotificationPermissionAction(opensSettings = !canAsk) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canAsk) {
      launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      SettingsLinks.open(context, CheckId.NOTIFICATIONS, xiaomiFamily)
    }
  }
}

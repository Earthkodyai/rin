package io.github.earthkodyai.rinalarm.setup

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/**
 * Opens the Settings page that fixes a check. Each check has a list of candidates, most specific first, ending in
 * the app's info page, which every phone has. The HyperOS pages are undocumented components that Xiaomi can move or
 * lock down in any update, so every launch is guarded.
 */
object SettingsLinks {
  private const val TAG = "RinSetup"

  /** Returns false if no candidate could be opened. */
  fun open(context: Context, check: CheckId, xiaomiFamily: Boolean): Boolean {
    for (intent in candidates(context, check, xiaomiFamily)) {
      try {
        context.startActivity(intent)
        return true
      } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "$check: ${intent.component ?: intent.action} not found")
      } catch (e: SecurityException) {
        // A non-exported OEM activity.
        Log.i(TAG, "$check: ${intent.component ?: intent.action} refused (${e.javaClass.simpleName})")
      }
    }
    return false
  }

  private fun candidates(context: Context, check: CheckId, xiaomiFamily: Boolean): List<Intent> {
    val pkg = context.packageName
    val packageUri = "package:$pkg".toUri()
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    val specific =
      when (check) {
        CheckId.NOTIFICATIONS ->
          listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        CheckId.FULL_SCREEN ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
          } else {
            emptyList()
          }
        CheckId.EXACT_ALARMS ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
          } else {
            emptyList()
          }
        CheckId.ALARM_VOLUME -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
        CheckId.DO_NOT_DISTURB ->
          listOf(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
        CheckId.BATTERY ->
          buildList {
            if (xiaomiFamily) {
              // HyperOS per-app battery page ("No restrictions").
              add(
                Intent()
                  .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                  .putExtra("package_name", pkg)
                  .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
              )
            }
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
          }
        CheckId.LOCK_SCREEN ->
          listOf(
            // HyperOS "Other permissions" for this app, where "Show on Lock screen" lives.
            Intent("miui.intent.action.APP_PERM_EDITOR")
              .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
              .putExtra("extra_pkgname", pkg)
          )
        // Runtime permissions live on the app's info page.
        CheckId.MISSIONS -> emptyList()
        CheckId.AUTOSTART ->
          listOf(
            Intent()
              .setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
              )
          )
      }
    return specific + appInfo
  }
}

package io.github.earthkodyai.rinalarm.alarm.receiver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.earthkodyai.rinalarm.alarm.ring.RingActivity
import io.github.earthkodyai.rinalarm.alarm.ring.RingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The manifest attributes the ring path depends on, read back from the installed package. Losing one of them breaks
 * rings only in situations unit tests never see (after a reboot, from the background).
 */
@RunWith(AndroidJUnit4::class)
class ReceiverManifestTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val packageManager = context.packageManager
  private val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE

  private fun component(cls: Class<*>) = ComponentName(context, cls)

  @Test
  fun everyActionTheReceiverHandles_isRegisteredForIt() {
    for (action in RescheduleReceiver.ACTIONS) {
      val receivers =
        packageManager.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), flags).map {
          it.activityInfo.name
        }
      assertEquals(action, listOf(RescheduleReceiver::class.java.name), receivers)
    }
  }

  @Test
  fun ringPath_runsBeforeTheFirstUnlock() {
    // Direct Boot: S1 showed HyperOS delivers LOCKED_BOOT_COMPLETED to apps without Autostart, BOOT_COMPLETED not.
    assertTrue(packageManager.getReceiverInfo(component(AlarmFireReceiver::class.java), flags).directBootAware)
    assertTrue(packageManager.getReceiverInfo(component(RescheduleReceiver::class.java), flags).directBootAware)
    assertTrue(packageManager.getServiceInfo(component(RingService::class.java), flags).directBootAware)
    assertTrue(packageManager.getActivityInfo(component(RingActivity::class.java), flags).directBootAware)
  }

  @Test
  fun fireReceiver_andRingComponents_areNotExported() {
    assertFalse(packageManager.getReceiverInfo(component(AlarmFireReceiver::class.java), flags).exported)
    assertFalse(packageManager.getServiceInfo(component(RingService::class.java), flags).exported)
    assertFalse(packageManager.getActivityInfo(component(RingActivity::class.java), flags).exported)
  }

  @Test
  fun ringService_isSystemExempted() {
    // S1: systemExempted may start from the background and from boot; mediaPlayback may not on Android 15+.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    assertEquals(
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
      packageManager.getServiceInfo(component(RingService::class.java), flags).foregroundServiceType,
    )
  }
}

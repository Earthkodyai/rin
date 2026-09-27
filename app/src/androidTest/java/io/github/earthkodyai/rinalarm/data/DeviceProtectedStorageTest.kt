package io.github.earthkodyai.rinalarm.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.data.db.RinDatabase
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The alarm receivers must read alarms before the first unlock after a reboot (Direct Boot), so the database and
 * settings have to live in device-protected storage. Uses scratch file names so the real alarms are never touched.
 */
@RunWith(AndroidJUnit4::class)
class DeviceProtectedStorageTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val deviceProtectedDir = context.createDeviceProtectedStorageContext().dataDir.canonicalPath
  private val databaseFile = StorageFiles.database(context, "storage-test.db")
  private val settingsFile = StorageFiles.settings(context, "storage-test")

  @After
  fun tearDown() {
    StorageFiles.deviceProtected(context).deleteDatabase(databaseFile.absolutePath)
    settingsFile.delete()
  }

  @Test
  fun defaultPaths_areInsideDeviceProtectedStorage() {
    assertTrue(StorageFiles.database(context).canonicalPath.startsWith(deviceProtectedDir))
    assertTrue(StorageFiles.settings(context).canonicalPath.startsWith(deviceProtectedDir))
  }

  @Test
  fun database_writesItsFileWhereStorageFilesSays() = runTest {
    val database = RinDatabase.open(context, databaseFile)
    try {
      RoomAlarmRepository(database.alarmDao()).save(Alarm(time = LocalTime.of(6, 30)))
      assertTrue(databaseFile.exists())
      assertTrue(databaseFile.canonicalPath.startsWith(deviceProtectedDir))
      assertEquals(1, database.alarmDao().getEnabled().size)
    } finally {
      database.close()
    }
  }

  @Test
  fun settings_roundTripInDeviceProtectedStorage() = runTest {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    try {
      val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { settingsFile })
      settings.setOnboardingCompleted(true)
      assertTrue(settings.onboardingCompleted.first())
      assertTrue(settingsFile.exists())
      assertTrue(settingsFile.canonicalPath.startsWith(deviceProtectedDir))
    } finally {
      scope.cancel()
    }
  }
}

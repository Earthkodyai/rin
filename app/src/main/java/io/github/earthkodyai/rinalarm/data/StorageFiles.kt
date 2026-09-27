package io.github.earthkodyai.rinalarm.data

import android.content.Context
import java.io.File

/**
 * Where persistent files live. Everything goes in device-protected storage so the alarm receivers can read it
 * after a reboot, before the user unlocks the phone (Direct Boot, LOCKED_BOOT_COMPLETED). Only non-sensitive
 * alarm data belongs here: device-protected files are readable whenever the phone is powered on.
 *
 * Paths are built from the device-protected context's own dirs on purpose. Helpers such as
 * `preferencesDataStoreFile()` go through `applicationContext`, which silently points back at credential-protected
 * storage.
 */
object StorageFiles {
  private const val DATABASE_NAME = "rinalarm.db"
  private const val SETTINGS_NAME = "settings"

  fun deviceProtected(context: Context): Context =
    if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()

  fun database(context: Context, name: String = DATABASE_NAME): File = deviceProtected(context).getDatabasePath(name)

  fun settings(context: Context, name: String = SETTINGS_NAME): File =
    File(deviceProtected(context).filesDir, "datastore/$name.preferences_pb")
}

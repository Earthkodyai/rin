package io.github.earthkodyai.rinalarm.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.earthkodyai.rinalarm.data.StorageFiles
import java.io.File

/**
 * Schema history:
 * 1. alarms (task 1.1)
 * 2. ring options on alarms, pending_rings, ring_events (task 1.2)
 * 3. alarms.isTest for the Diagnostics test alarm (task 1.4)
 * 4. alarms.mission (task 3.1)
 * 5. alarms.sound (UX.7)
 * 6. alarms.difficulty and alarms.scold (G.1)
 */
@Database(
  entities = [AlarmEntity::class, PendingRingEntity::class, RingEventEntity::class],
  version = 6,
  exportSchema = true,
  autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class RinDatabase : RoomDatabase() {
  abstract fun alarmDao(): AlarmDao

  abstract fun pendingRingDao(): PendingRingDao

  abstract fun ringEventDao(): RingEventDao

  companion object {
    /** Opens the database at [file]. An absolute path keeps it there whichever context Room resolves names with. */
    fun open(context: Context, file: File): RinDatabase =
      Room.databaseBuilder(StorageFiles.deviceProtected(context), RinDatabase::class.java, file.absolutePath).build()
  }
}

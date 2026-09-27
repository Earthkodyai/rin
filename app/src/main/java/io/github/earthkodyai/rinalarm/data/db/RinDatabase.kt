package io.github.earthkodyai.rinalarm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.earthkodyai.rinalarm.data.StorageFiles
import java.io.File

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = true)
abstract class RinDatabase : RoomDatabase() {
  abstract fun alarmDao(): AlarmDao

  companion object {
    /** Opens the database at [file]. An absolute path keeps it there whichever context Room resolves names with. */
    fun open(context: Context, file: File): RinDatabase =
      Room.databaseBuilder(StorageFiles.deviceProtected(context), RinDatabase::class.java, file.absolutePath).build()
  }
}

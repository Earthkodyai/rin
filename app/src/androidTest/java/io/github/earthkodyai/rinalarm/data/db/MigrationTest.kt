package io.github.earthkodyai.rinalarm.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema step is tested against the committed schema JSON in app/schemas. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RinDatabase::class.java)

  @Test
  fun v1to2_keepsAlarms_andGivesThemTheDefaultRingOptions() {
    helper.createDatabase(DB, 1).use {
      it.execSQL("INSERT INTO alarms (id, hour, minute, repeatDays, label, enabled) VALUES (1, 7, 0, 31, 'Work', 1)")
    }
    val db = helper.runMigrationsAndValidate(DB, 2, true)

    db.query("SELECT hour, label, rampSeconds, vibrate, snoozeMinutes, maxSnoozes FROM alarms WHERE id = 1").use {
      it.moveToFirst()
      val defaults = RingOptions()
      assertEquals(7, it.getInt(0))
      assertEquals("Work", it.getString(1))
      assertEquals(defaults.rampSeconds, it.getInt(2))
      assertEquals(1, it.getInt(3))
      assertEquals(defaults.snoozeMinutes, it.getInt(4))
      assertEquals(defaults.maxSnoozes, it.getInt(5))
    }
    db.query("SELECT COUNT(*) FROM pending_rings").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    db.query("SELECT COUNT(*) FROM ring_events").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
  }

  private companion object {
    const val DB = "migration-test.db"
  }
}

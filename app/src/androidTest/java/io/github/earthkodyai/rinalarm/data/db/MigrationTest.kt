package io.github.earthkodyai.rinalarm.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionChoice
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

  @Test
  fun v2to3_keepsAlarms_asRealOnes() {
    helper.createDatabase(DB, 2).use {
      it.execSQL("INSERT INTO alarms (id, hour, minute, repeatDays, label, enabled) VALUES (1, 6, 30, 0, 'Gym', 1)")
    }
    val db = helper.runMigrationsAndValidate(DB, 3, true)

    db.query("SELECT label, isTest FROM alarms WHERE id = 1").use {
      it.moveToFirst()
      assertEquals("Gym", it.getString(0))
      assertEquals(0, it.getInt(1))
    }
  }

  @Test
  fun v3to4_givesExistingAlarmsRinPicks_andKeepsTheTestAlarm() {
    helper.createDatabase(DB, 3).use {
      it.execSQL("INSERT INTO alarms (id, hour, minute, repeatDays, label, enabled) VALUES (1, 6, 30, 127, 'Work', 1)")
      it.execSQL(
        "INSERT INTO alarms (id, hour, minute, repeatDays, label, enabled, isTest) VALUES (2, 7, 0, 0, 'Test', 1, 1)"
      )
    }
    val db = helper.runMigrationsAndValidate(DB, 4, true)

    db.query("SELECT label, mission FROM alarms ORDER BY id").use {
      it.moveToFirst()
      assertEquals("Work", it.getString(0))
      assertEquals(MissionChoice.DEFAULT_STORED, it.getString(1))
      // A test alarm left over from before the update: rings with Rin picks once, then is deleted as usual.
      it.moveToNext()
      assertEquals(MissionChoice.DEFAULT_STORED, it.getString(1))
    }
  }

  @Test
  fun v5to6_givesExistingAlarmsEasy_andRinStillScolds() {
    helper.createDatabase(DB, 5).use {
      it.execSQL("INSERT INTO alarms (id, hour, minute, repeatDays, label, enabled) VALUES (1, 6, 30, 31, 'Work', 1)")
    }
    val db = helper.runMigrationsAndValidate(DB, 6, true)

    db.query("SELECT label, difficulty, scold FROM alarms WHERE id = 1").use {
      it.moveToFirst()
      assertEquals("Work", it.getString(0))
      assertEquals(Difficulty.EASY.stored, it.getString(1))
      assertEquals(1, it.getInt(2))
    }
  }

  private companion object {
    const val DB = "migration-test.db"
  }
}

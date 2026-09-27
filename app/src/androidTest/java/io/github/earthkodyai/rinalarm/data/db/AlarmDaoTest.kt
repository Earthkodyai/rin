package io.github.earthkodyai.rinalarm.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.data.RoomAlarmRepository
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {
  private lateinit var database: RinDatabase
  private lateinit var repository: RoomAlarmRepository

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RinDatabase::class.java).build()
    repository = RoomAlarmRepository(database.alarmDao())
  }

  @After fun tearDown() = database.close()

  private suspend fun save(alarm: Alarm): Long {
    val rowId = database.alarmDao().upsert(alarm.toEntity())
    return if (alarm.id == 0L) rowId else alarm.id
  }

  @Test
  fun upsert_insertsThenUpdates_andKeepsTheId() = runTest {
    val id = save(Alarm(time = LocalTime.of(7, 0)))
    val updatedId = save(Alarm(id = id, time = LocalTime.of(7, 30), repeatDays = RepeatDays.WEEKDAYS))

    assertEquals(id, updatedId)
    assertEquals(
      listOf(Alarm(id = id, time = LocalTime.of(7, 30), repeatDays = RepeatDays.WEEKDAYS)),
      repository.alarms.first(),
    )
  }

  @Test
  fun alarms_areSortedByTimeOfDay() = runTest {
    save(Alarm(time = LocalTime.of(9, 0)))
    save(Alarm(time = LocalTime.of(6, 15)))
    save(Alarm(time = LocalTime.of(6, 5)))

    assertEquals(
      listOf(LocalTime.of(6, 5), LocalTime.of(6, 15), LocalTime.of(9, 0)),
      repository.alarms.first().map { it.time },
    )
  }

  @Test
  fun getEnabled_skipsAlarmsThatAreOff() = runTest {
    val on = save(Alarm(time = LocalTime.of(7, 0)))
    save(Alarm(time = LocalTime.of(8, 0), enabled = false))

    assertEquals(listOf(on), database.alarmDao().getEnabled().map { it.id })
  }

  @Test
  fun get_returnsTheAlarm() = runTest {
    val id = save(Alarm(time = LocalTime.of(7, 0), label = "Work"))
    assertEquals(Alarm(id = id, time = LocalTime.of(7, 0), label = "Work"), repository.get(id))
  }

  @Test
  fun delete_removesTheAlarm() = runTest {
    val id = save(Alarm(time = LocalTime.of(7, 0)))
    database.alarmDao().delete(id)

    assertNull(repository.get(id))
  }
}

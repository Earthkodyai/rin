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

  @Test
  fun save_insertsThenUpdates_andKeepsTheId() = runTest {
    val id = repository.save(Alarm(time = LocalTime.of(7, 0)))
    val updatedId = repository.save(Alarm(id = id, time = LocalTime.of(7, 30), repeatDays = RepeatDays.WEEKDAYS))

    assertEquals(id, updatedId)
    assertEquals(
      listOf(Alarm(id = id, time = LocalTime.of(7, 30), repeatDays = RepeatDays.WEEKDAYS)),
      repository.alarms.first(),
    )
  }

  @Test
  fun alarms_areSortedByTimeOfDay() = runTest {
    repository.save(Alarm(time = LocalTime.of(9, 0)))
    repository.save(Alarm(time = LocalTime.of(6, 15)))
    repository.save(Alarm(time = LocalTime.of(6, 5)))

    assertEquals(
      listOf(LocalTime.of(6, 5), LocalTime.of(6, 15), LocalTime.of(9, 0)),
      repository.alarms.first().map { it.time },
    )
  }

  @Test
  fun getEnabled_skipsAlarmsThatAreOff() = runTest {
    val on = repository.save(Alarm(time = LocalTime.of(7, 0)))
    repository.save(Alarm(time = LocalTime.of(8, 0), enabled = false))

    assertEquals(listOf(on), database.alarmDao().getEnabled().map { it.id })
  }

  @Test
  fun delete_removesTheAlarm() = runTest {
    val id = repository.save(Alarm(time = LocalTime.of(7, 0)))
    repository.delete(id)

    assertNull(database.alarmDao().getById(id))
  }
}

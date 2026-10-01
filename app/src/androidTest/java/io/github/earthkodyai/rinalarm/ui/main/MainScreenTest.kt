package io.github.earthkodyai.rinalarm.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

  @Test
  fun emptyList_showsEmptyState() {
    composeTestRule.setContent { MainScreen(MainScreenUiState.Success(emptyList()), {}, {}, { _, _ -> }) }

    composeTestRule.onNodeWithText(string(R.string.alarms_empty)).assertExists()
  }

  @Test
  fun switchedOffAlarm_isMarkedOff() {
    val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS, enabled = false)
    composeTestRule.setContent {
      MainScreen(MainScreenUiState.Success(listOf(AlarmRow(alarm, nextRing = null))), {}, {}, { _, _ -> })
    }

    composeTestRule.onNodeWithText(string(R.string.alarm_off)).assertExists()
  }

  @Test
  fun dayDots_readAsTheRepeatSummary_andTheHeaderSaysTomorrow() {
    val zone = ZoneId.of("Asia/Bangkok")
    val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS)
    val next = ZonedDateTime.of(2026, 9, 29, 7, 0, 0, 0, zone)
    composeTestRule.setContent {
      MainScreen(MainScreenUiState.Success(listOf(AlarmRow(alarm, next)), today = LocalDate.of(2026, 9, 28)), {}, {}, { _, _ -> })
    }

    composeTestRule.onNodeWithContentDescription(string(R.string.repeat_weekdays)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.home_next_tomorrow, ""), substring = true).assertExists()
  }

  @Test
  fun rinLine_showsInHerBubble() {
    composeTestRule.setContent {
      MainScreen(MainScreenUiState.Success(emptyList()), {}, {}, { _, _ -> }, rinLine = "Morning!")
    }

    composeTestRule.onNodeWithText("Morning!").assertExists()
  }

  @Test
  fun rowTapEdits_switchToggles_andAddButtonAdds() {
    val alarm = Alarm(id = 5, time = LocalTime.of(7, 0), label = "Work", enabled = false)
    val events = mutableListOf<String>()
    composeTestRule.setContent {
      MainScreen(
        MainScreenUiState.Success(listOf(AlarmRow(alarm, nextRing = null))),
        onAdd = { events += "add" },
        onEdit = { events += "edit $it" },
        onToggle = { id, on -> events += "toggle $id $on" },
      )
    }

    composeTestRule.onNodeWithText("Work").performClick()
    composeTestRule.onNodeWithContentDescription("Alarm at", substring = true).assertIsOff().performClick()
    composeTestRule.onNodeWithText(string(R.string.alarm_add)).performClick()

    assertEquals(listOf("edit 5", "toggle 5 true", "add"), events)
  }
}

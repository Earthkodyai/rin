package io.github.earthkodyai.rinalarm.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun emptyList_showsEmptyState() {
    composeTestRule.setContent { MainScreen(MainScreenUiState.Success(emptyList())) }

    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.alarms_empty)).assertExists()
  }

  @Test
  fun switchedOffAlarm_isMarkedOff() {
    val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS, enabled = false)
    composeTestRule.setContent { MainScreen(MainScreenUiState.Success(listOf(AlarmRow(alarm, nextRing = null)))) }

    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.repeat_weekdays)).assertExists()
    composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.alarm_off)).assertExists()
  }
}

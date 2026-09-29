package io.github.earthkodyai.rinalarm.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmEditorScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val alarm = Alarm(id = 3, time = LocalTime.of(6, 30), repeatDays = RepeatDays.WEEKDAYS, label = "Work")
  private val calls = mutableListOf<String>()
  private var closed = 0

  private val actions =
    object : AlarmEditorActions {
      override fun setTime(value: LocalTime) {
        calls += "time $value"
      }

      override fun toggleDay(day: DayOfWeek) {
        calls += "day $day"
      }

      override fun setLabel(value: String) {
        calls += "label $value"
      }

      override fun setRampSeconds(value: Int) {
        calls += "ramp $value"
      }

      override fun setVibrate(value: Boolean) {
        calls += "vibrate $value"
      }

      override fun setSnoozeMinutes(value: Int) {
        calls += "snooze $value"
      }

      override fun setMaxSnoozes(value: Int) {
        calls += "maxSnoozes $value"
      }

      override fun setMission(value: MissionChoice) {
        calls += "mission ${value.stored}"
      }

      override fun allowMission(type: MissionType) {
        calls += "allow ${type.stored}"
      }

      override fun save() {
        calls += "save"
      }

      override fun delete() {
        calls += "delete"
      }
    }

  private fun string(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

  private fun show(draft: Alarm = alarm, isNew: Boolean = false, hasChanges: Boolean = false) {
    val state = AlarmEditorUiState.Editing(draft, isNew, hasChanges, ringsIn = Duration.ofMinutes(30), busy = false)
    composeTestRule.setContent { AlarmEditorScreen(state, actions, onClose = { closed++ }) }
  }

  @Test
  fun mission_choiceAndAllow_callTheActions() {
    val state =
      AlarmEditorUiState.Editing(
        alarm,
        isNew = false,
        hasChanges = false,
        ringsIn = null,
        busy = false,
        missionReadiness = mapOf(MissionType.PADS to Readiness.NO_PERMISSION),
      )
    composeTestRule.setContent { AlarmEditorScreen(state, actions, onClose = {}) }

    composeTestRule.onNodeWithText(string(R.string.mission_choice_none)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.mission_allow)).performScrollTo().performClick()

    assertEquals(listOf("mission none", "allow pads"), calls)
  }

  @Test
  fun controls_callTheActions() {
    show()
    val wednesday = DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())

    composeTestRule.onNodeWithContentDescription(wednesday).performClick()
    composeTestRule.onNodeWithText(string(R.string.duration_seconds, 60)).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(VIBRATE_TAG).performScrollTo().assertIsOn().performClick()
    composeTestRule.onNodeWithText("5").performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.duration_minutes, 10)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.editor_save)).performClick()

    assertEquals(
      listOf("day WEDNESDAY", "ramp 60", "vibrate false", "maxSnoozes 5", "snooze 10", "save"),
      calls,
    )
  }

  @Test
  fun noSnoozesAllowed_greysOutTheSnoozeLength() {
    show(alarm.copy(ring = RingOptions(maxSnoozes = 0)))
    composeTestRule.onNodeWithText(string(R.string.duration_minutes, 10)).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun delete_asksFirst_andCancelKeepsTheAlarm() {
    show()
    composeTestRule.onNodeWithText(string(R.string.editor_delete)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.delete_title)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.editor_cancel)).performClick()
    assertTrue(calls.isEmpty())

    composeTestRule.onNodeWithText(string(R.string.editor_delete)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.delete_confirm)).performClick()
    assertEquals(listOf("delete"), calls)
  }

  @Test
  fun newAlarm_hasNoDeleteButton() {
    show(isNew = true)
    composeTestRule.onNodeWithText(string(R.string.editor_title_new)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.editor_delete)).assertDoesNotExist()
  }

  @Test
  fun back_withoutChanges_closesAtOnce() {
    show()
    composeTestRule.onNodeWithContentDescription(string(R.string.editor_back)).performClick()
    assertEquals(1, closed)
  }

  @Test
  fun systemBack_withChanges_asksBeforeDiscarding() {
    show(hasChanges = true)

    Espresso.pressBack()
    composeTestRule.onNodeWithText(string(R.string.discard_title)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.discard_keep)).performClick()
    assertEquals(0, closed)

    composeTestRule.onNodeWithContentDescription(string(R.string.editor_back)).performClick()
    composeTestRule.onNodeWithText(string(R.string.discard_confirm)).performClick()
    assertEquals(1, closed)
  }
}

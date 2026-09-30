package io.github.earthkodyai.rinalarm.ui.editor

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Bangkok, Mon 28 Sep 2026 06:00. */
class AlarmEditorViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val zone = ZoneId.of("Asia/Bangkok")
  private val time = FixedTimeSource(LocalDateTime.parse("2026-09-28T06:00").atZone(zone).toInstant(), zone)
  private val work =
    Alarm(id = 7, time = LocalTime.of(6, 30), repeatDays = RepeatDays.WEEKDAYS, label = "Work", enabled = false)
  private val alarms = FakeAlarms(listOf(work))

  private var missions = mapOf(MissionType.PADS to Readiness.NO_PERMISSION)

  private val moments = HomeMoments()

  private fun editor(id: Long) = AlarmEditorViewModel(id, alarms, alarms, time, { missions }, moments)

  // --- mission (task 3.1) ---

  @Test
  fun newAlarm_letsRinPick_andSavesAChangedMission() = runTest {
    val editor = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    assertEquals(MissionChoice.RinPicks, editor.editing().draft.mission)

    editor.setMission(MissionChoice.None)
    assertTrue(editor.editing().hasChanges)
    editor.save()
    editor.finished()

    assertEquals(MissionChoice.None, alarms.saves.last().mission)
  }

  @Test
  fun missionProblems_explainWhyTheChoiceCannotRun_untilThePermissionArrives() = runTest {
    val editor = editor(work.id)
    assertEquals(mapOf(MissionType.PADS to Readiness.NO_PERMISSION), editor.editing().missionProblems)

    editor.setMission(MissionChoice.None)
    assertEquals(emptyMap<MissionType, Readiness>(), editor.editing().missionProblems)

    editor.setMission(MissionChoice.Only(MissionType.PADS))
    missions = mapOf(MissionType.PADS to Readiness.READY)
    editor.refreshMissions()
    assertEquals(emptyMap<MissionType, Readiness>(), editor.editing().missionProblems)
  }

  @Test
  fun hiddenQr_isNeitherOfferedNorAProblem_norAChoice() = runTest {
    missions = mapOf(MissionType.PADS to Readiness.READY, MissionType.QR to Readiness.NOT_SET_UP)
    val editor = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    assertEquals(emptyMap<MissionType, Readiness>(), editor.editing().missionProblems)
    assertEquals(emptyList<MissionType>(), editor.editing().missionOffers)
    assertEquals(
      listOf(
        MissionChoice.RinPicks,
        MissionChoice.Only(MissionType.PADS),
        MissionChoice.Only(MissionType.CUPS),
        MissionChoice.Only(MissionType.SPEECH),
        MissionChoice.None,
      ),
      RingChoices.MISSIONS,
    )
  }

  private suspend fun AlarmEditorViewModel.editing() =
    uiState.first { it is AlarmEditorUiState.Editing } as AlarmEditorUiState.Editing

  private suspend fun AlarmEditorViewModel.finished() =
    uiState.first { it !is AlarmEditorUiState.Editing && it !is AlarmEditorUiState.Loading }

  // --- opening ---

  @Test
  fun newAlarm_startsFromTheDefaults_withNoChanges() = runTest {
    val state = editor(AlarmEditorViewModel.NEW_ALARM_ID).editing()

    assertTrue(state.isNew)
    assertFalse(state.hasChanges)
    assertEquals(LocalTime.of(7, 0), state.draft.time)
    assertEquals(RingOptions(), state.draft.ring)
    assertEquals(Duration.ofHours(1), state.ringsIn)
  }

  @Test
  fun existingAlarm_loadsIt() = runTest {
    val state = editor(work.id).editing()

    assertFalse(state.isNew)
    assertEquals(work, state.draft)
    // Switched off, but the preview shows when it will ring once saved (saving switches it on).
    assertEquals(Duration.ofMinutes(30), state.ringsIn)
  }

  @Test
  fun missingAlarm_isNotFound() = runTest {
    assertEquals(AlarmEditorUiState.NotFound, editor(99).finished())
  }

  // --- editing ---

  @Test
  fun edits_changeTheDraftOnly_andRevertingClearsHasChanges() = runTest {
    val viewModel = editor(work.id)
    viewModel.setTime(LocalTime.of(5, 45))

    val changed = viewModel.editing()
    assertTrue(changed.hasChanges)
    assertEquals(LocalTime.of(5, 45), changed.draft.time)
    assertEquals(listOf(work), alarms.alarms.first())

    viewModel.setTime(LocalTime.of(6, 30))
    assertFalse(viewModel.editing().hasChanges)
  }

  @Test
  fun setTime_dropsSeconds() = runTest {
    val viewModel = editor(work.id)
    viewModel.setTime(LocalTime.of(5, 45, 30, 5))
    assertEquals(LocalTime.of(5, 45), viewModel.editing().draft.time)
  }

  @Test
  fun toggleDay_updatesTheRepeatDaysAndTheNextRing() = runTest {
    val viewModel = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    viewModel.toggleDay(DayOfWeek.WEDNESDAY)

    val state = viewModel.editing()
    assertEquals(RepeatDays.of(DayOfWeek.WEDNESDAY), state.draft.repeatDays)
    assertEquals(Duration.ofHours(49), state.ringsIn) // Mon 06:00 -> Wed 07:00
  }

  @Test
  fun setLabel_capsTheLengthAndKeepsItOnOneLine() = runTest {
    val viewModel = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    viewModel.setLabel("Gym\n" + "x".repeat(60))

    val label = viewModel.editing().draft.label
    assertEquals(RingChoices.LABEL_MAX, label.length)
    assertTrue(label.startsWith("Gym x"))
  }

  @Test
  fun ringOptions_areEditable() = runTest {
    val viewModel = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    viewModel.setRampSeconds(60)
    viewModel.setVibrate(false)
    viewModel.setSnoozeMinutes(10)
    viewModel.setMaxSnoozes(0)

    assertEquals(
      RingOptions(rampSeconds = 60, vibrate = false, snoozeMinutes = 10, maxSnoozes = 0),
      viewModel.editing().draft.ring,
    )
  }

  // --- saving and deleting ---

  @Test
  fun save_switchesTheAlarmOn_trimsTheLabel_andReportsWhenItRings() = runTest {
    val viewModel = editor(work.id)
    viewModel.setLabel("  Work  ")
    viewModel.save()

    assertEquals(AlarmEditorUiState.Saved(Duration.ofMinutes(30)), viewModel.finished())
    assertEquals(listOf(work.copy(enabled = true)), alarms.saves)
    // Rin says so on the home screen (task 4.2).
    assertTrue(moments.alarmSaved.tryReceive().isSuccess)
  }

  @Test
  fun save_newAlarm_insertsIt() = runTest {
    val viewModel = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    viewModel.save()

    viewModel.finished()
    assertEquals(listOf(AlarmEditorViewModel.NEW_ALARM), alarms.saves)
    assertEquals(2, alarms.alarms.first().size)
  }

  @Test
  fun save_tappedTwiceWhileWriting_savesOnce_andIgnoresEditsMeanwhile() = runTest {
    val gate = CompletableDeferred<Unit>()
    alarms.gate = gate
    val viewModel = editor(work.id)
    viewModel.editing()

    viewModel.save()
    viewModel.save()
    viewModel.setTime(LocalTime.of(9, 0))
    assertTrue(viewModel.editing().busy)
    gate.complete(Unit)

    viewModel.finished()
    assertEquals(listOf(work.copy(enabled = true)), alarms.saves)
  }

  @Test
  fun save_whenTheWriteFails_keepsTheDraftAndSaysSo() = runTest {
    alarms.failure = IOException("disk full")
    val viewModel = editor(work.id)
    viewModel.setTime(LocalTime.of(5, 0))
    viewModel.save()

    val state = viewModel.uiState.first { it is AlarmEditorUiState.Editing && it.failed } as AlarmEditorUiState.Editing
    assertFalse(state.busy)
    assertEquals(LocalTime.of(5, 0), state.draft.time)
    assertTrue(moments.alarmSaved.tryReceive().isFailure) // nothing saved: Rin says nothing

    // The next edit clears the message; a retry that works then closes the editor.
    alarms.failure = null
    viewModel.setTime(LocalTime.of(5, 5))
    assertFalse(viewModel.editing().failed)
    viewModel.save()
    assertTrue(viewModel.finished() is AlarmEditorUiState.Saved)
  }

  @Test
  fun delete_removesTheAlarm() = runTest {
    val viewModel = editor(work.id)
    viewModel.editing()
    viewModel.delete()

    assertEquals(AlarmEditorUiState.Deleted, viewModel.finished())
    assertNull(alarms.get(work.id))
  }

  @Test
  fun delete_onANewAlarm_doesNothing() = runTest {
    val viewModel = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    viewModel.delete()

    assertFalse(viewModel.editing().busy)
    assertEquals(listOf(work), alarms.alarms.first())
  }

  // --- choices ---

  @Test
  fun withCurrent_addsAnOutOfListValueInOrder() {
    assertEquals(listOf(0, 15, 30, 45, 60), RingChoices.withCurrent(RingChoices.RAMP_SECONDS, 45))
    assertEquals(RingChoices.MAX_SNOOZES, RingChoices.withCurrent(RingChoices.MAX_SNOOZES, 3))
  }

  @Test
  fun defaults_areAmongTheChoices() {
    val defaults = RingOptions()
    assertTrue(defaults.rampSeconds in RingChoices.RAMP_SECONDS)
    assertTrue(defaults.snoozeMinutes in RingChoices.SNOOZE_MINUTES)
    assertTrue(defaults.maxSnoozes in RingChoices.MAX_SNOOZES)
  }
}

package io.github.earthkodyai.rinalarm.ui.editor

import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.ring.MusicTheme
import io.github.earthkodyai.rinalarm.alarm.ring.PreviewSounds
import io.github.earthkodyai.rinalarm.alarm.ring.RingSound
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeSettings
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import io.github.earthkodyai.rinalarm.mission.MissionPlanner
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

  private var themes = emptyList<MusicTheme>()

  private val previews = FakePreviews()

  private val settings = FakeSettings()

  private fun editor(id: Long) = AlarmEditorViewModel(id, alarms, alarms, time, { missions }, moments, { themes }, previews, settings)

  // --- mission (task 3.1) ---

  @Test
  fun firstAlarmWalkthrough_stepsThroughEveryPart_skippingTryForNone_andSoundWithoutThemes() {
    fun walk(mission: MissionChoice, hasSounds: Boolean) =
      generateSequence(GuideStep.TIME) { it.next(mission, hasSounds) }.toList()
    assertEquals(GuideStep.entries, walk(MissionChoice.RinPicks, hasSounds = true))
    assertEquals(
      listOf(GuideStep.TIME, GuideStep.DAYS, GuideStep.GAME, GuideStep.SOUND, GuideStep.SAVE),
      walk(MissionChoice.None, hasSounds = true),
    )
    assertEquals(
      listOf(GuideStep.TIME, GuideStep.DAYS, GuideStep.GAME, GuideStep.TRY, GuideStep.SAVE),
      walk(MissionChoice.Only(MissionType.CUPS), hasSounds = false),
    )
    assertTrue(GuideStep.SAVE.last)
  }

  @Test
  fun tryThisGame_isTheChosenGame_RinsPickForTheNextRing_orNothingForNone() = runTest {
    missions = MissionType.entries.associateWith { Readiness.READY }
    val editor = editor(7)
    editor.editing()
    // Work rings next on Mon 28 Sep (06:30, switched on for the question): Rin's pick for that day.
    val day = java.time.LocalDate.of(2026, 9, 28)
    assertEquals(MissionPlanner.rotate(MissionType.entries, day), editor.gameToTry())

    editor.setMission(MissionChoice.Only(MissionType.CUPS))
    assertEquals(MissionType.CUPS, editor.gameToTry())
    // A game that is not ready still plays its practice round.
    missions = mapOf(MissionType.SPEECH to Readiness.NO_PERMISSION)
    editor.refreshMissions()
    editor.setMission(MissionChoice.Only(MissionType.SPEECH))
    assertEquals(MissionType.SPEECH, editor.gameToTry())

    editor.setMission(MissionChoice.None)
    assertNull(editor.gameToTry())
  }

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
  fun missionChoices_areRinPicksTheThreeGamesAndNone() = runTest {
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

  // --- level and scold switch (G.1) ---

  @Test
  fun aFirstNewAlarm_isNormal_andRinScolds() = runTest {
    val draft = editor(AlarmEditorViewModel.NEW_ALARM_ID).editing().draft
    assertEquals(Difficulty.NORMAL, draft.difficulty)
    assertTrue(draft.scold)
  }

  @Test
  fun aNewAlarm_startsAtTheLastLevelAndScoldPicked_andSavingOneRemembersThem() = runTest {
    settings.lastDifficulty.value = Difficulty.HARD
    settings.lastScold.value = false
    val editor = editor(AlarmEditorViewModel.NEW_ALARM_ID)
    val state = editor.editing()
    assertEquals(Difficulty.HARD, state.draft.difficulty)
    assertFalse(state.draft.scold)
    assertFalse("the remembered picks are no change", state.hasChanges)

    editor.setDifficulty(Difficulty.NIGHTMARE)
    editor.setScold(true)
    assertEquals(Difficulty.NIGHTMARE to true, editor.practiceOptions())
    editor.save()
    editor.finished()
    assertEquals(Difficulty.NIGHTMARE, alarms.saves.single().difficulty)
    assertEquals(Difficulty.NIGHTMARE, settings.lastDifficulty.value)
    assertTrue(settings.lastScold.value)
  }

  @Test
  fun anOldAlarmSavedUntouched_leavesTheRememberedPicksAlone_butAChangedOneSetsThem() = runTest {
    settings.lastDifficulty.value = Difficulty.HARD
    val untouched = editor(work.id)
    untouched.editing()
    untouched.save()
    untouched.finished()
    assertEquals(Difficulty.EASY, alarms.saves.last().difficulty)
    assertEquals(Difficulty.HARD, settings.lastDifficulty.value)

    val changed = editor(work.id)
    changed.editing()
    changed.setScold(false)
    changed.save()
    changed.finished()
    assertFalse(settings.lastScold.value)
    assertEquals(Difficulty.HARD, settings.lastDifficulty.value)
  }

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

  // --- sound preview (the user, 2026-10-02) ---

  @Test
  fun pickSound_choosesIt_andPlaysAFewSecondsThatFadeOut() = runTest {
    themes = listOf(MusicTheme("magic", "Magic morning"), MusicTheme("cafe", "Sweet bistro"))
    val editor = editor(work.id)
    editor.editing()

    editor.pickSound(AlarmSound.Theme("cafe"))
    val sound = previews.sounds.single()
    assertEquals(listOf<String?>("cafe"), previews.opened)
    assertTrue(sound.playing)
    assertEquals(AlarmSound.Theme("cafe"), editor.editing().draft.ring.sound)
    assertEquals(AlarmSound.Theme("cafe"), editor.editing().previewing)

    advanceTimeBy(AlarmEditorViewModel.PREVIEW_MS - AlarmEditorViewModel.FADE_MS - 1)
    assertEquals(1f, sound.gains.last())
    advanceTimeBy(AlarmEditorViewModel.FADE_MS / 2)
    assertTrue(sound.gains.last() in 0.1f..0.5f)
    advanceTimeBy(AlarmEditorViewModel.FADE_MS)
    assertEquals(0f, sound.gains.last())
    assertTrue(sound.released)
    assertNull(editor.editing().previewing)
    // The choice stays once the preview ends.
    assertEquals(AlarmSound.Theme("cafe"), editor.editing().draft.ring.sound)
  }

  @Test
  fun pickSound_onThePlayingTile_stopsIt_andKeepsTheChoice() = runTest {
    themes = listOf(MusicTheme("magic", "Magic morning"))
    val editor = editor(work.id)
    editor.editing()

    editor.pickSound(AlarmSound.Beep)
    editor.pickSound(AlarmSound.Beep)

    assertEquals(listOf<String?>(null), previews.opened)
    assertTrue(previews.sounds.single().released)
    assertNull(editor.editing().previewing)
    assertEquals(AlarmSound.Beep, editor.editing().draft.ring.sound)

    // Once stopped, a tap plays it again.
    editor.pickSound(AlarmSound.Beep)
    assertEquals(2, previews.sounds.size)
    assertEquals(AlarmSound.Beep, editor.editing().previewing)
    editor.stopPreview()
  }

  @Test
  fun pickSound_onAnotherTile_cutsTheFirstPreview() = runTest {
    themes = listOf(MusicTheme("magic", "Magic morning"), MusicTheme("cafe", "Sweet bistro"))
    val editor = editor(work.id)
    editor.editing()

    editor.pickSound(AlarmSound.Theme("magic"))
    editor.pickSound(AlarmSound.Theme("cafe"))

    assertEquals(listOf<String?>("magic", "cafe"), previews.opened)
    assertTrue(previews.sounds[0].released)
    assertFalse(previews.sounds[1].released)
    assertEquals(AlarmSound.Theme("cafe"), editor.editing().previewing)

    // The first preview's timer is gone with it: the second still plays its full length.
    advanceTimeBy(AlarmEditorViewModel.PREVIEW_MS - 100)
    assertEquals(AlarmSound.Theme("cafe"), editor.editing().previewing)
    editor.stopPreview()
    assertTrue(previews.sounds[1].released)
  }

  @Test
  fun rinPicks_playsTheThemeOfTheNextRing() = runTest {
    themes = listOf(MusicTheme("magic", "Magic morning"), MusicTheme("cafe", "Sweet bistro"))
    val ids = themes.map { it.id }
    // It is Monday 06:00; a 05:00 alarm next rings on Tuesday, when Rin picks the other theme.
    val early = work.copy(id = 8, time = LocalTime.of(5, 0), repeatDays = RepeatDays.EVERY_DAY)
    alarms.save(early)
    val editor = editor(early.id)
    editor.editing()

    editor.pickSound(AlarmSound.RinPicks)

    val tuesday = AlarmSound.resolve(AlarmSound.RinPicks, ids, LocalDate.of(2026, 9, 29))
    val monday = AlarmSound.resolve(AlarmSound.RinPicks, ids, LocalDate.of(2026, 9, 28))
    assertEquals(listOf(tuesday), previews.opened)
    assertTrue(tuesday != monday)
    assertEquals(AlarmSound.RinPicks, editor.editing().previewing)
    editor.stopPreview()
  }

  @Test
  fun save_silencesThePreview() = runTest {
    themes = listOf(MusicTheme("magic", "Magic morning"))
    val editor = editor(work.id)
    editor.editing()

    editor.pickSound(AlarmSound.Theme("magic"))
    editor.save()

    assertTrue(editor.finished() is AlarmEditorUiState.Saved)
    assertTrue(previews.sounds.single().released)
    assertEquals(AlarmSound.Theme("magic"), alarms.saves.last().ring.sound)
  }

  private class FakeSound : RingSound {
    val gains = mutableListOf<Float>()
    var playing = false
    var released = false

    override fun setGain(gain: Float) {
      gains += gain
    }

    override fun play() {
      playing = true
    }

    override fun pause() {
      playing = false
    }

    override fun release() {
      playing = false
      released = true
    }
  }

  private class FakePreviews : PreviewSounds {
    val opened = mutableListOf<String?>()
    val sounds = mutableListOf<FakeSound>()

    override fun open(themeId: String?): RingSound {
      opened += themeId
      return FakeSound().also { sounds += it }
    }
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

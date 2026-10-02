package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.lifecycle.SavedStateHandle
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.character.CupsView
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.RinSpeaker
import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.mission.CupsMission
import io.github.earthkodyai.rinalarm.mission.CupsPhase
import io.github.earthkodyai.rinalarm.mission.CupsState
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.Hush
import io.github.earthkodyai.rinalarm.mission.Mission
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionFactory
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionProgress
import io.github.earthkodyai.rinalarm.mission.MissionReadiness
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.mission.MissionState
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Miss
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsMission
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadsState
import io.github.earthkodyai.rinalarm.mission.MissionPlanner
import io.github.earthkodyai.rinalarm.mission.RepeatMission
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.RepeatState
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.dialogue.pouty
import io.github.earthkodyai.rinalarm.testing.FakeLineVoice
import io.github.earthkodyai.rinalarm.testing.FakeAlarms
import io.github.earthkodyai.rinalarm.testing.FakeSettings
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import io.github.earthkodyai.rinalarm.testing.quietLineBook
import io.github.earthkodyai.rinalarm.testing.realLineBook
import io.github.earthkodyai.rinalarm.testing.realScript
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RingViewModelTest {
  @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

  private val ringState = RingState()
  private val log = RecordingLog()
  private val mission = FakeMission()
  private var clockMs = 1_000L
  private val readiness = MissionReadiness { MissionType.entries.associateWith { Readiness.READY } }
  private val time = FixedTimeSource(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC)
  private val request =
    RingRequest(4, LocalTime.of(6, 30), "Work", Instant.parse("2026-09-27T23:30:00Z"), 0, 3, late = false, RingOptions())
  /** Quiet unless a test gives her the real script (task 4.2 tests below). */
  private var book = quietLineBook
  private val voice = FakeLineVoice()
  private val settings = FakeSettings()
  private val alarms = FakeAlarms()

  private fun TestScope.ringScreen(): Pair<RingViewModel, MutableList<RingCommand>> {
    val viewModel = RingViewModel(ringState, { mission }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
    val commands = mutableListOf<RingCommand>()
    backgroundScope.launch { viewModel.commands.collect { commands += it } }
    runCurrent()
    return viewModel to commands
  }

  @Test
  fun passingTheMission_stopsTheRing_thenClosesAfterTheCelebration() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val (viewModel, commands) = ringScreen()
      assertEquals(1, mission.starts)
      assertFalse(viewModel.uiState.value.plainDismiss)

      clockMs = 5_000
      mission.state.value = MissionProgress(12, 30)
      runCurrent()
      assertEquals(5_000L, ringState.lastProgressAt)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)

      mission.state.value = MissionProgress(30, 30, MissionState.PASSED)
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_MISSION)), commands)
      assertTrue(viewModel.uiState.value.passed)
      assertEquals(listOf(RingEventType.MISSION_STARTED, RingEventType.MISSION_PASSED), log.types)
      assertTrue(mission.stopped)

      // RingService stops the ring; Rin claps before the screen closes.
      ringState.set(null)
      runCurrent()
      assertFalse(viewModel.uiState.value.finished)
      advanceTimeBy(RingViewModel.CELEBRATE_MS + 1)
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun noMission_offersThePlainDismiss_andClosesAsSoonAsTheRingEnds() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, mission = null))
      val (viewModel, commands) = ringScreen()
      assertEquals(0, mission.starts)
      assertTrue(viewModel.uiState.value.plainDismiss)

      viewModel.dismiss()
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_SCREEN)), commands)

      ringState.set(null)
      runCurrent()
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun aBrokenMission_fallsBackToThePlainDismiss() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val (viewModel, _) = ringScreen()

      mission.state.value = MissionProgress(3, 30, MissionState.FAILED)
      runCurrent()

      assertTrue(viewModel.uiState.value.plainDismiss)
      assertEquals(listOf(RingEventType.MISSION_STARTED, RingEventType.MISSION_FAILED), log.types)
    }

  @Test
  fun stoppingProgress_makesHerPout_andTheEmergencyHoldSaysSo() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val (viewModel, commands) = ringScreen()
      mission.state.value = MissionProgress(5, 30)
      runCurrent()

      advanceTimeBy(RingPolicy.MISSION_IDLE.toMillis() - 1)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)
      clockMs += RingPolicy.MISSION_IDLE.toMillis()
      advanceTimeBy(2)
      assertEquals(RingPhase.STALLED, viewModel.uiState.value.phase)

      viewModel.emergencyStop()
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_EMERGENCY)), commands)
    }

  @Test
  fun activityQuietsTheTone_likeProgressDoes() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val (viewModel, _) = ringScreen()
      clockMs = 7_000
      mission.state.value = MissionProgress(0, 30, activity = 1)
      runCurrent()
      assertEquals(7_000L, ringState.lastProgressAt)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)
    }

  @Test
  fun letsPlay_quietsTheToneAtOnce_beforeHerIntroEnds() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val viewModel = RingViewModel(ringState, { pads }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      runCurrent()
      assertEquals(null, ringState.lastProgressAt)
      clockMs = 7_000
      viewModel.startGame()
      // No runCurrent: her intro has not even started, and the game has not begun.
      assertEquals(7_000L, ringState.lastProgressAt)
      assertEquals(emptyList<String>(), pads.calls)
    }

  @Test
  fun colourPads_forwardsPlayAndTaps_andAMissCutsToRinSulking() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val viewModel = RingViewModel(ringState, { pads }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
      val cues = mutableListOf<Gesture>()
      backgroundScope.launch { viewModel.cues.collect { cues += it } }
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      runCurrent()
      assertEquals(PadsPhase.READY, viewModel.uiState.value.pads?.phase)

      viewModel.startGame()
      runCurrent()
      viewModel.tapPad(Pad.BLUE)
      assertEquals(listOf("begin", "tap BLUE"), pads.calls)

      pads.game.value = PadsState(PadsPhase.SCOLD, miss = Miss.WRONG)
      runCurrent()
      assertEquals(RingMoods.SCOLD_MOOD, viewModel.uiState.value.mood)
      assertEquals(Gesture.HUFF, cues.last())

      // Back to the game: her mood follows the ring's phase again.
      pads.game.value = PadsState(PadsPhase.DEMO)
      runCurrent()
      assertEquals(RingMoods.mood(viewModel.uiState.value.phase), viewModel.uiState.value.mood)
      assertEquals(1, cues.count { it == Gesture.HUFF })
    }

  private fun TestScope.cupsScreen(cups: FakeCupsMission): Pair<RingViewModel, MutableList<Gesture>> {
    val viewModel = RingViewModel(ringState, { cups }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
    val cues = mutableListOf<Gesture>()
    backgroundScope.launch { viewModel.cues.collect { cues += it } }
    ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.CUPS)))
    runCurrent()
    return viewModel to cues
  }

  @Test
  fun cups_forwardsPlayAndPicks_andAWrongPickMakesHerSulk() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, cues) = cupsScreen(cups)
      viewModel.startGame()
      runCurrent()
      // The table comes into view first; the ball shows only once her page says it is there.
      assertTrue(viewModel.uiState.value.cupsStaging)
      assertEquals(emptyList<String>(), cups.calls)
      viewModel.onCupsView(CupsView.Shown(listOf(0.3f, 0.5f, 0.7f)))
      assertFalse(viewModel.uiState.value.cupsStaging)
      viewModel.startGame() // a second tap does not restart it
      viewModel.pickCup(2)
      assertEquals(listOf("begin", "pick 2"), cups.calls)

      cups.game.value = CupsState(CupsPhase.REVEAL, right = false, picks = 1, mistakes = 1)
      runCurrent()
      assertTrue(viewModel.uiState.value.cupsScold)
      assertEquals(RingMoods.SCOLD_MOOD, viewModel.uiState.value.mood)
      assertEquals(Gesture.HUFF, cues.last())

      // A right pick is no scold.
      cups.game.value = CupsState(CupsPhase.REVEAL, streak = 1, right = true, picks = 2, mistakes = 1)
      runCurrent()
      assertFalse(viewModel.uiState.value.cupsScold)
      assertEquals(1, cues.count { it == Gesture.HUFF })
    }

  @Test
  fun cups_theNativeBoardTakesOver_whenHerPageDoesNotShowTheTableInTime() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _) = cupsScreen(cups)
      viewModel.startGame()
      advanceTimeBy(RingViewModel.CUPS_PAGE_WAIT_MS - 1)
      assertFalse(viewModel.uiState.value.cups2d)
      assertEquals(emptyList<String>(), cups.calls)
      advanceTimeBy(2)
      assertTrue(viewModel.uiState.value.cups2d)
      assertEquals(listOf("begin"), cups.calls) // the 2D board plays at once
      // Her page turning up late changes nothing for this ring.
      viewModel.onCupsView(CupsView.Shown(listOf(0.3f, 0.5f, 0.7f)))
      assertTrue(viewModel.uiState.value.cups2d)

      cups.progress.value = MissionProgress(3, 3, MissionState.PASSED, activity = 4)
      runCurrent()
      val passed = log.details[log.types.indexOf(RingEventType.MISSION_PASSED)]
      assertTrue(passed, passed.endsWith(" board=2d:page_timeout"))
    }

  @Test
  fun cups_aPageThatIsGone_startsTheNativeBoardAtOnce() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _) = cupsScreen(cups)
      viewModel.onCupsView(CupsView.Unavailable) // no model in this build, or the renderer died before the game
      viewModel.startGame()
      runCurrent()
      assertTrue(viewModel.uiState.value.cups2d)
      assertEquals(listOf("begin"), cups.calls)
    }

  @Test
  fun cups_staysOnHerPage_onceItShowsTheTable_butNotIfThePageDies() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _) = cupsScreen(cups)
      viewModel.startGame()
      viewModel.onCupsView(CupsView.Shown(listOf(0.3f, 0.5f, 0.7f)))
      advanceTimeBy(RingViewModel.CUPS_PAGE_WAIT_MS * 2)
      assertFalse(viewModel.uiState.value.cups2d)
      assertEquals(CupsView.Shown(listOf(0.3f, 0.5f, 0.7f)), viewModel.uiState.value.cupsView)

      viewModel.onCupsView(CupsView.Unavailable) // the renderer crashed: the still shows
      assertTrue(viewModel.uiState.value.cups2d)
    }

  private class FakeCupsMission : CupsMission {
    override val type = MissionType.CUPS
    override val progress = MutableStateFlow(MissionProgress(0, 3))
    override val game = MutableStateFlow(CupsState())
    val calls = mutableListOf<String>()

    override fun start() = Unit

    override fun stop() = Unit

    override fun begin() {
      calls += "begin"
      game.value = CupsState(CupsPhase.SHOW)
    }

    override fun pick(slot: Int) {
      calls += "pick $slot"
    }

    override fun skipScold() {
      calls += "skip"
    }
  }

  @Test
  fun repeat_forwardsTheHushToTheRing_nodsOnARightSentence_andCantTalkSwitchesGame() =
    runTest(main.dispatcher) {
      val repeat = FakeRepeatMission()
      val pads = FakePadsMission()
      val viewModel =
        RingViewModel(ringState, { if (it == MissionType.SPEECH) repeat else pads }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
      val cues = mutableListOf<Gesture>()
      backgroundScope.launch { viewModel.cues.collect { cues += it } }
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.SPEECH)))
      runCurrent()
      assertEquals(MissionType.SPEECH, viewModel.uiState.value.missionType)

      viewModel.startGame()
      runCurrent()
      viewModel.hearAgain()
      viewModel.tapWord(2)
      assertEquals(listOf("begin", "again", "tap 2"), repeat.calls)

      // The mic opens: the ring hears about it at once.
      repeat.hush.value = Hush.SILENT
      runCurrent()
      assertEquals(Hush.SILENT, ringState.hush.value)

      repeat.game.value = RepeatState(RepeatPhase.FEEDBACK, feedback = Feedback.RIGHT)
      runCurrent()
      assertEquals(Gesture.NOD, cues.last())

      // "Can't talk right now": another game takes over, the hush is lifted, and both steps are logged.
      viewModel.cantTalk()
      runCurrent()
      assertEquals(Hush.NONE, ringState.hush.value)
      assertTrue(repeat.stopped)
      // Picked the way Rin picks, among the games that need no mic.
      val expected = MissionPlanner.rotate(listOf(MissionType.PADS, MissionType.CUPS), LocalDate.of(2026, 9, 29))
      assertEquals(expected, viewModel.uiState.value.missionType)
      assertEquals(null, viewModel.uiState.value.repeat)
      val switched = log.details[log.types.indexOf(RingEventType.MISSION_SWITCHED)]
      assertTrue(switched, switched.startsWith("from=speech to=") && "reason=cant_talk" in switched && "game=fake" in switched)
      assertEquals(RingEventType.MISSION_STARTED, log.types.last())
      assertTrue(log.details.last(), log.details.last().endsWith("switchedFrom=speech"))
    }

  // --- Rin's lines (task 4.2) ---

  private val RingViewModel.line
    get() = uiState.value.line

  /** Lets the line on screen run out (a subtitle alone: its reading time). */
  private fun TestScope.untilSaid(viewModel: RingViewModel) {
    val line = viewModel.line ?: return
    advanceTimeBy(RinSpeaker.readingMs(line) + 1)
    runCurrent()
  }

  private fun TestScope.talkingScreen(
    missions: MissionFactory,
    ring: ActiveRing,
    faceUp: Boolean = true,
  ): Triple<RingViewModel, MutableList<RingCommand>, MutableList<Gesture>> {
    book = realLineBook()
    val viewModel = RingViewModel(ringState, missions, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
    if (faceUp) viewModel.onCharacterVisible()
    val commands = mutableListOf<RingCommand>()
    val cues = mutableListOf<Gesture>()
    backgroundScope.launch { viewModel.commands.collect { commands += it } }
    backgroundScope.launch { viewModel.cues.collect { cues += it } }
    ringState.set(ring)
    runCurrent()
    return Triple(viewModel, commands, cues)
  }

  @Test
  fun theRing_opensWithHerLine_asASubtitle_inItsMood_andLeavesTheToneAloneWithoutAClip() =
    runTest(main.dispatcher) {
      // Tuesday 06:30: a weekday, past the sleepy hours.
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val line = checkNotNull(viewModel.line)
      assertEquals("ring.cheerful", line.pool)
      assertEquals(line.emotion, viewModel.uiState.value.mood)
      assertEquals(Hush.NONE, ringState.hush.value)
      untilSaid(viewModel)
      assertNull(viewModel.line)
      assertEquals(RingMoods.mood(RingPhase.WAKING), viewModel.uiState.value.mood)
    }

  @Test
  fun herOpeningLine_waitsForHerFace_thenAMomentForItsFirstFrames() =
    runTest(main.dispatcher) {
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)), faceUp = false)
      advanceTimeBy(2_000)
      runCurrent()
      assertNull("her page is still loading", viewModel.line)

      viewModel.onCharacterVisible()
      runCurrent()
      assertNull(viewModel.line)
      advanceTimeBy(RingViewModel.OPENING_SETTLE_MS + 1)
      runCurrent()
      assertEquals("ring.cheerful", checkNotNull(viewModel.line).pool)
    }

  @Test
  fun herOpeningLine_stillComes_whenHerPageNeverLoads() =
    runTest(main.dispatcher) {
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)), faceUp = false)
      advanceTimeBy(RingViewModel.OPENING_WAIT_MS + 1)
      runCurrent()
      assertEquals("ring.cheerful", checkNotNull(viewModel.line).pool)
    }

  @Test
  fun herClip_ducksTheTone_onlyWhileItPlays() =
    runTest(main.dispatcher) {
      voice.clips = realScript.lines.map { it.id }.toSet()
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      assertEquals(Hush.QUIET, ringState.hush.value)
      assertEquals(listOf(viewModel.line!!.id), voice.played)
      advanceTimeBy(1_001)
      assertEquals(Hush.NONE, ringState.hush.value)
    }

  @Test
  fun afterTheLastSnooze_sheOpensWithABackLastLine() =
    runTest(main.dispatcher) {
      val again = request.copy(snoozeCount = 3, snoozesLeft = 0)
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(again, MissionPlan.Run(MissionType.PADS)))
      assertEquals("back.last", viewModel.line?.pool)
    }

  @Test
  fun aRestDayRing_hasNoGame_opensWithItsDayModeLine_andNeverPouts() =
    runTest(main.dispatcher) {
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, null, DayModeKind.REST))
      assertEquals("p5.rest", viewModel.line?.pool)
      assertTrue(viewModel.uiState.value.plainDismiss)
      assertEquals(0, mission.starts)
      assertTrue(viewModel.uiState.value.calm)
    }

  @Test
  fun aSickDayRing_afterASnooze_stillSaysItsDayModeLine() =
    runTest(main.dispatcher) {
      val again = request.copy(snoozeCount = 2, snoozesLeft = 1)
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(again, null, DayModeKind.SICK))
      assertEquals("p5.sick", viewModel.line?.pool)
    }

  @Test
  fun scoldOff_aSnoozeAfterTheFirst_isQuiet_andTheLastSnoozesReturnHasNoPoutyLine() =
    runTest(main.dispatcher) {
      val again = request.copy(snoozeCount = 3, snoozesLeft = 0, scold = false)
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(again, MissionPlan.Run(MissionType.PADS)))
      // back.last is all pouty lines: she says nothing rather than pout.
      assertNull(viewModel.line)
    }

  @Test
  fun scoldOff_aMissedPadsRound_getsNoHuff_noScold_andACalmFace() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, _, cues) = talkingScreen({ pads }, ActiveRing(request.copy(scold = false), MissionPlan.Run(MissionType.PADS)))
      untilSaid(viewModel)
      viewModel.startGame()
      runCurrent()
      untilSaid(viewModel)
      pads.game.value = PadsState(PadsPhase.SCOLD, miss = Miss.WRONG)
      runCurrent()
      assertNull(viewModel.line)
      assertFalse(cues.any { it.pouty })
      assertFalse(viewModel.uiState.value.mood.pouty)
    }

  @Test
  fun letsPlay_waitsForHerIntro_andARightRoundGetsNoLine() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, _, _) = talkingScreen({ pads }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.startGame()
      runCurrent()
      assertTrue(viewModel.line!!.pool in setOf("game.intro", "game.intro.pads"))
      assertEquals(emptyList<String>(), pads.calls)
      untilSaid(viewModel)
      assertEquals(listOf("begin"), pads.calls)

      // D23: during the game she speaks only after a mistake.
      pads.game.value = PadsState(PadsPhase.DEMO, round = 1)
      runCurrent()
      assertNull(viewModel.line)
    }

  @Test
  fun aScold_thenAWin_isAHardWin_thenTheClosingRemark_thenTheScreenCloses() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, commands, cues) = talkingScreen({ pads }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.startGame()
      runCurrent()
      untilSaid(viewModel)
      pads.game.value = PadsState(PadsPhase.SCOLD, miss = Miss.SLOW)
      runCurrent()
      assertEquals("pads.slow", viewModel.line?.pool)
      assertEquals(Mood.POUTY, viewModel.uiState.value.mood)
      untilSaid(viewModel)

      pads.progress.value = MissionProgress(3, 3, MissionState.PASSED)
      runCurrent()
      assertEquals(RingCommand.Dismiss(RingService.SOURCE_MISSION), commands.last())
      val won = checkNotNull(viewModel.line)
      assertEquals("won.hard", won.pool)
      assertEquals(won.gesture ?: Gesture.CLAP, cues.last())
      ringState.set(null) // RingService ends the ring; the screen stays for her lines
      runCurrent()
      untilSaid(viewModel)
      // 06:30 rings before breakfast: a general remark or a breakfast one.
      assertTrue(viewModel.line!!.pool in setOf("after.remark", "after.meal.breakfast"))
      assertFalse(viewModel.uiState.value.finished)
      untilSaid(viewModel)
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun aCleanWin_saysSo_andATapClosesTheScreenAtOnce() =
    runTest(main.dispatcher) {
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.close() // still ringing: a tap does not close it
      assertFalse(viewModel.uiState.value.finished)
      mission.state.value = MissionProgress(30, 30, MissionState.PASSED)
      runCurrent()
      assertEquals("won.clean", viewModel.line?.pool)
      viewModel.close()
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun aSnooze_getsItsLine_andTheScreenWaitsForIt() =
    runTest(main.dispatcher) {
      val (viewModel, commands, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.snooze()
      runCurrent()
      assertEquals(listOf(RingCommand.Snooze), commands)
      assertTrue(viewModel.uiState.value.leaving)
      assertEquals("snooze.first", viewModel.line?.pool)
      ringState.set(null)
      runCurrent()
      assertFalse(viewModel.uiState.value.finished)
      untilSaid(viewModel)
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun theEmergencyStop_getsItsLine_andATapClosesAtOnce() =
    runTest(main.dispatcher) {
      val (viewModel, commands, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.emergencyStop()
      runCurrent()
      assertEquals(listOf(RingCommand.Dismiss(RingService.SOURCE_EMERGENCY)), commands)
      assertEquals("emergency", viewModel.line?.pool)
      ringState.set(null)
      runCurrent()
      viewModel.close()
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun cups_aRightPickGetsNoLine_aWrongOneHerScold() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _, _) = talkingScreen({ cups }, ActiveRing(request, MissionPlan.Run(MissionType.CUPS)))
      viewModel.onCupsView(CupsView.Unavailable)
      viewModel.startGame()
      runCurrent()
      untilSaid(viewModel)
      assertEquals(listOf("begin"), cups.calls)

      cups.game.value = CupsState(CupsPhase.REVEAL, streak = 1, right = true, picks = 1)
      runCurrent()
      assertNull(viewModel.line)

      cups.game.value = CupsState(CupsPhase.REVEAL, right = false, picks = 2, mistakes = 1)
      runCurrent()
      assertEquals("cups.wrong", viewModel.line?.pool)
      assertEquals(listOf("begin"), cups.calls) // the game keeps its own time
    }

  @Test
  fun repeat_aMissedTryGetsHerLine_aRightOneOnlyHerNod() =
    runTest(main.dispatcher) {
      val repeat = FakeRepeatMission()
      val (viewModel, _, _) = talkingScreen({ repeat }, ActiveRing(request, MissionPlan.Run(MissionType.SPEECH)))
      repeat.game.value = RepeatState(RepeatPhase.FEEDBACK, index = 0, tries = 1, feedback = Feedback.MISSED)
      runCurrent()
      assertEquals("speech.missed", viewModel.line?.pool)
      repeat.game.value = RepeatState(RepeatPhase.LISTENING, index = 0, tries = 2)
      runCurrent()
      repeat.game.value = RepeatState(RepeatPhase.FEEDBACK, index = 0, tries = 2, feedback = Feedback.NOTHING)
      runCurrent()
      assertEquals("speech.totap", viewModel.line?.pool)
      repeat.game.value = RepeatState(RepeatPhase.TAPPING, index = 1)
      runCurrent()
      untilSaid(viewModel)
      repeat.game.value = RepeatState(RepeatPhase.FEEDBACK, index = 1, tries = 1, feedback = Feedback.RIGHT)
      runCurrent()
      assertNull(viewModel.line)
    }

  private class FakeRepeatMission : RepeatMission {
    override val type = MissionType.SPEECH
    override val progress: StateFlow<MissionProgress> = MutableStateFlow(MissionProgress(0, 3))
    override val game = MutableStateFlow(RepeatState())
    override val hush = MutableStateFlow(Hush.NONE)
    override val speaking = MutableStateFlow<Speaking?>(null)
    override val micLevel = MutableStateFlow(0f)
    val calls = mutableListOf<String>()
    var stopped = false

    override fun start() = Unit

    override fun stop() {
      stopped = true
    }

    override fun summary() = "game=fake"

    override fun begin() {
      calls += "begin"
    }

    override fun hearAgain() {
      calls += "again"
    }

    override fun tapWord(chip: Int) {
      calls += "tap $chip"
    }
  }

  private class FakePadsMission : PadsMission {
    override val type = MissionType.PADS
    override val progress = MutableStateFlow(MissionProgress(0, 3))
    override val game = MutableStateFlow(PadsState())
    val calls = mutableListOf<String>()

    override fun start() = Unit

    override fun stop() = Unit

    override fun begin() {
      calls += "begin"
    }

    override fun tap(pad: Pad) {
      calls += "tap $pad"
    }

    override fun skipScold() {
      calls += "skip"
    }
  }

  // --- Skipping her lines and the scold switch (G.1) ---

  /** A pads ring past "Let's play" and her intro, then a wrong tap: she scolds. */
  private fun TestScope.scoldedPads(scold: Boolean = true): Triple<RingViewModel, FakePadsMission, MutableList<Gesture>> {
    val pads = FakePadsMission()
    val (viewModel, _, cues) = talkingScreen({ pads }, ActiveRing(request.copy(scold = scold), MissionPlan.Run(MissionType.PADS)))
    untilSaid(viewModel)
    viewModel.startGame()
    runCurrent()
    untilSaid(viewModel)
    pads.game.value = PadsState(PadsPhase.SCOLD, miss = Miss.WRONG)
    runCurrent()
    return Triple(viewModel, pads, cues)
  }

  @Test
  fun aTapDuringHerIntro_cutsIt_andTheGameStartsAtOnce() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, _, _) = talkingScreen({ pads }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      untilSaid(viewModel)
      viewModel.startGame()
      runCurrent()
      assertTrue(checkNotNull(viewModel.line).pool.startsWith("game.intro"))
      assertTrue("the game waits for her intro", pads.calls.isEmpty())

      viewModel.skip()
      runCurrent()
      assertNull(viewModel.line)
      assertEquals(listOf("begin"), pads.calls)
    }

  @Test
  fun inAGame_herLinesArmGesturesAreLeftOut_butTheWinStillClaps() =
    runTest(main.dispatcher) {
      // Every intro raises her arms (joy, as game.intro.07 does); the won line has no gesture, so the win claps.
      book = LineBook { pool, _, _ -> if (pool.startsWith("game.intro")) Line("i", pool, "Go!", Mood.CHEERFUL, Gesture.JOY) else null }
      val pads = FakePadsMission()
      val viewModel = RingViewModel(ringState, { pads }, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
      viewModel.onCharacterVisible()
      val cues = mutableListOf<Gesture>()
      backgroundScope.launch { viewModel.cues.collect { cues += it } }
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      runCurrent()
      viewModel.startGame()
      runCurrent()
      assertTrue(viewModel.uiState.value.inGame)
      assertFalse(Gesture.JOY in cues)

      pads.progress.value = MissionProgress(3, 3, MissionState.PASSED)
      runCurrent()
      assertFalse(viewModel.uiState.value.inGame)
      assertEquals(Gesture.CLAP, cues.last())
    }

  @Test
  fun letsPlayTappedAgainDuringHerIntro_startsTheGameAtOnce() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, _, _) = talkingScreen({ pads }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      viewModel.startGame()
      runCurrent()
      assertTrue(pads.calls.isEmpty())
      viewModel.startGame()
      runCurrent()
      assertNull(viewModel.line)
      assertEquals(listOf("begin"), pads.calls)
    }

  @Test
  fun letsPlay_cutsHerOpeningLine_forTheIntro() =
    runTest(main.dispatcher) {
      val pads = FakePadsMission()
      val (viewModel, _, _) = talkingScreen({ pads }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      assertEquals("ring.cheerful", viewModel.line?.pool)
      viewModel.startGame()
      runCurrent()
      assertTrue(checkNotNull(viewModel.line).pool.startsWith("game.intro"))
    }

  @Test
  fun aTapDuringHerScold_cutsIt_andTheNextTryStartsNow() =
    runTest(main.dispatcher) {
      val (viewModel, pads, _) = scoldedPads()
      assertEquals("pads.wrong", viewModel.line?.pool)
      assertTrue(viewModel.uiState.value.scolding)
      assertTrue(viewModel.uiState.value.showScoldSwitch)

      viewModel.skip()
      runCurrent()
      assertNull(viewModel.line)
      assertEquals(listOf("begin", "skip"), pads.calls)
    }

  @Test
  fun aTapWithHerSilent_skipsTheScoldOfAnAlarmThatDoesNotScold() =
    runTest(main.dispatcher) {
      val (viewModel, pads, _) = scoldedPads(scold = false)
      assertNull(viewModel.line)
      assertFalse("no switch for an alarm already calm", viewModel.uiState.value.showScoldSwitch)
      viewModel.skip()
      assertEquals(listOf("begin", "skip"), pads.calls)
    }

  @Test
  fun scoldOff_aPadsMiss_isQuiet_aRedCrossForAMoment_thenTheGameGoesOn() =
    runTest(main.dispatcher) {
      val (viewModel, pads, cues) = scoldedPads(scold = false)
      val state = viewModel.uiState.value
      assertTrue(state.quietMiss)
      assertNull(viewModel.line)
      assertFalse(cues.any { it.pouty })
      assertFalse(state.mood.pouty)
      assertEquals(listOf("begin"), pads.calls)

      advanceTimeBy(RingViewModel.QUIET_MISS_PADS_MS + 1)
      runCurrent()
      assertEquals(listOf("begin", "skip"), pads.calls)
      pads.game.value = PadsState(PadsPhase.DEMO)
      runCurrent()
      assertFalse(viewModel.uiState.value.quietMiss)
    }

  @Test
  fun scoldOff_aWrongCup_isQuiet_andTheCupsComeDownAfterTheirMoment() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _, _) = talkingScreen({ cups }, ActiveRing(request.copy(scold = false), MissionPlan.Run(MissionType.CUPS)))
      viewModel.onCupsView(CupsView.Unavailable)
      viewModel.startGame()
      runCurrent()
      untilSaid(viewModel)
      cups.game.value = CupsState(CupsPhase.REVEAL, right = false, picks = 1, mistakes = 1)
      runCurrent()
      assertTrue(viewModel.uiState.value.quietMiss)
      assertNull(viewModel.line)

      advanceTimeBy(RingViewModel.QUIET_MISS_PADS_MS + 1)
      runCurrent()
      assertEquals("the cups need longer: they rise first", listOf("begin"), cups.calls)
      advanceTimeBy(RingViewModel.QUIET_MISS_CUPS_MS - RingViewModel.QUIET_MISS_PADS_MS)
      runCurrent()
      assertEquals(listOf("begin", "skip"), cups.calls)
    }

  @Test
  fun aGameButton_cutsHerLineShort() =
    runTest(main.dispatcher) {
      val cups = FakeCupsMission()
      val (viewModel, _, _) = talkingScreen({ cups }, ActiveRing(request, MissionPlan.Run(MissionType.CUPS)))
      viewModel.onCupsView(CupsView.Unavailable)
      viewModel.startGame()
      runCurrent()
      untilSaid(viewModel)
      cups.game.value = CupsState(CupsPhase.REVEAL, right = false, picks = 1, mistakes = 1)
      runCurrent()
      assertEquals("cups.wrong", viewModel.line?.pool)

      viewModel.pickCup(1)
      runCurrent()
      assertNull(viewModel.line)
      assertEquals(listOf("begin", "pick 1"), cups.calls)
    }

  @Test
  fun afterTheWin_aTapNeitherCutsHerWonLine_norSkipsAnything() =
    runTest(main.dispatcher) {
      val (viewModel, _, _) = talkingScreen({ mission }, ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      mission.state.value = MissionProgress(30, 30, MissionState.PASSED)
      runCurrent()
      viewModel.skip()
      viewModel.tapPad(Pad.RED)
      runCurrent()
      assertEquals("won.clean", viewModel.line?.pool)
    }

  @Test
  fun scoldSwitchOff_silencesHerAtOnce_andSticksToThisAlarmAndNewOnes() =
    runTest(main.dispatcher) {
      alarms.save(Alarm(id = request.alarmId, time = request.time))
      val (viewModel, pads, cues) = scoldedPads()
      assertTrue(viewModel.uiState.value.mood.pouty)

      viewModel.setScold(false)
      runCurrent()
      assertNull(viewModel.line)
      val state = viewModel.uiState.value
      assertFalse(state.scold)
      assertFalse(state.mood.pouty)
      assertTrue("the switch stays for the rest of this scold", state.showScoldSwitch)
      assertFalse(checkNotNull(alarms.get(request.alarmId)).scold)
      assertFalse(settings.lastScold.value)
      assertEquals("scold=false", log.details[log.types.indexOf(RingEventType.SCOLD_SWITCHED)])

      // The next miss: no line, no huff, and no switch (it is off for this alarm now).
      val huffs = cues.count { it == Gesture.HUFF.onRing() }
      pads.game.value = PadsState(PadsPhase.DEMO, round = 1)
      runCurrent()
      pads.game.value = PadsState(PadsPhase.SCOLD, round = 1, miss = Miss.SLOW)
      runCurrent()
      assertNull(viewModel.line)
      assertEquals(huffs, cues.count { it == Gesture.HUFF.onRing() })
      assertFalse(viewModel.uiState.value.showScoldSwitch)
    }

  @Test
  fun scoldSwitchBackOn_undoesIt() =
    runTest(main.dispatcher) {
      alarms.save(Alarm(id = request.alarmId, time = request.time))
      val (viewModel, _, _) = scoldedPads()
      viewModel.setScold(false)
      viewModel.setScold(true)
      runCurrent()
      assertTrue(viewModel.uiState.value.scold)
      assertTrue(checkNotNull(alarms.get(request.alarmId)).scold)
      assertTrue(settings.lastScold.value)
    }

  @Test
  fun theRing_logsItsLevel_andBuildsTheGameAtIt() =
    runTest(main.dispatcher) {
      val levels = mutableListOf<Difficulty>()
      val missions =
        object : MissionFactory {
          override fun create(type: MissionType): Mission = error("the ring asks for its level")

          override fun create(type: MissionType, difficulty: Difficulty): Mission = mission.also { levels += difficulty }
        }
      ringState.set(ActiveRing(request.copy(difficulty = Difficulty.HARD), MissionPlan.Run(MissionType.PADS)))
      RingViewModel(ringState, missions, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms, SavedStateHandle())
      runCurrent()
      assertEquals(listOf(Difficulty.HARD), levels)
      assertTrue(log.details.first().contains("level=hard"))
    }

  @Test
  fun aPracticeRound_playsTheEditorsLevel_orElseTheNewAlarmOne() =
    runTest(main.dispatcher) {
      val levels = mutableListOf<Difficulty>()
      val missions =
        object : MissionFactory {
          override fun create(type: MissionType): Mission = error("a practice round never builds the real game")

          override fun practice(type: MissionType, difficulty: Difficulty): Mission = FakeMission().also { levels += difficulty }
        }
      fun open(vararg extras: Pair<String, Any>) {
        RingViewModel(
          ringState, missions, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms,
          SavedStateHandle(mapOf(RingViewModel.EXTRA_PRACTICE to MissionType.CUPS.stored, *extras)),
        )
        runCurrent()
      }
      open(RingViewModel.EXTRA_LEVEL to Difficulty.NIGHTMARE.stored, RingViewModel.EXTRA_SCOLD to false)
      settings.lastDifficulty.value = Difficulty.HARD
      open()
      assertEquals(listOf(Difficulty.NIGHTMARE, Difficulty.HARD), levels)
    }

  // --- Practice rounds (UX.8) ---

  /** A practice round of pads with the real script: every round its own fake mission, from [MissionFactory.practice]. */
  private fun TestScope.practiceScreen(rounds: MutableList<FakeMission>): Pair<RingViewModel, MutableList<RingCommand>> {
    book = realLineBook()
    val missions =
      object : MissionFactory {
        override fun create(type: MissionType): Mission = error("a practice round never builds the real game")

        override fun practice(type: MissionType, difficulty: Difficulty): Mission = FakeMission().also { rounds += it }
      }
    val viewModel =
      RingViewModel(
        ringState, missions, log, backgroundScope, { clockMs }, readiness, time, book, { voice }, settings, alarms,
        SavedStateHandle(mapOf(RingViewModel.EXTRA_PRACTICE to MissionType.PADS.stored)),
      )
    viewModel.onCharacterVisible()
    val commands = mutableListOf<RingCommand>()
    backgroundScope.launch { viewModel.commands.collect { commands += it } }
    runCurrent()
    return viewModel to commands
  }

  @Test
  fun aPracticeRound_runsWithNoRing_andTouchesNothingOfARealOne() =
    runTest(main.dispatcher) {
      val rounds = mutableListOf<FakeMission>()
      val (viewModel, commands) = practiceScreen(rounds)
      val state = viewModel.uiState.value
      assertTrue(state.practice)
      assertEquals(MissionType.PADS, state.missionType)
      assertEquals(1, rounds.single().starts)
      assertNull("no opening line: the round starts at Let's play", viewModel.line)

      viewModel.startGame()
      runCurrent()
      assertTrue(checkNotNull(viewModel.line).pool.startsWith("game.intro"))
      untilSaid(viewModel)
      clockMs = 5_000
      rounds.single().state.value = MissionProgress(0, 1, activity = 1)
      runCurrent()
      assertNull("a real ring's progress stays untouched", ringState.lastProgressAt)
      assertEquals(RingPhase.WORKING, viewModel.uiState.value.phase)

      rounds.single().state.value = MissionProgress(1, 1, MissionState.PASSED)
      runCurrent()
      assertTrue(viewModel.uiState.value.passed)
      assertEquals("won.clean", viewModel.line?.pool)
      untilSaid(viewModel)
      advanceTimeBy(60_000)
      runCurrent()
      assertFalse("the round waits for Play again or Done", viewModel.uiState.value.finished)
      assertNull(viewModel.line)
      assertTrue(commands.isEmpty())
      assertTrue(log.types.isEmpty())
      assertEquals(Hush.NONE, ringState.hush.value)
    }

  @Test
  fun playAgain_startsANewRound_andDone_closesTheScreen() =
    runTest(main.dispatcher) {
      val rounds = mutableListOf<FakeMission>()
      val (viewModel, _) = practiceScreen(rounds)
      viewModel.playAgain()
      assertEquals("no new round before a win", 1, rounds.size)

      rounds[0].state.value = MissionProgress(1, 1, MissionState.PASSED)
      runCurrent()
      viewModel.playAgain()
      runCurrent()
      assertEquals(2, rounds.size)
      assertTrue(rounds[0].stopped)
      assertFalse(viewModel.uiState.value.passed)
      assertTrue(viewModel.uiState.value.practice)
      assertNull(viewModel.line)

      viewModel.endPractice()
      runCurrent()
      assertTrue(rounds[1].stopped)
      assertTrue(viewModel.uiState.value.finished)
    }

  @Test
  fun aRealRing_ignoresPracticeOnlyActions() =
    runTest(main.dispatcher) {
      ringState.set(ActiveRing(request, MissionPlan.Run(MissionType.PADS)))
      val (viewModel, _) = ringScreen()
      assertFalse(viewModel.uiState.value.practice)
      viewModel.endPractice()
      mission.state.value = MissionProgress(30, 30, MissionState.PASSED)
      runCurrent()
      viewModel.playAgain()
      runCurrent()
      assertEquals(1, mission.starts)
      assertFalse(viewModel.uiState.value.finished)
    }

  private class FakeMission : Mission {
    override val type = MissionType.PADS
    val state = MutableStateFlow(MissionProgress(0, 30))
    override val progress: StateFlow<MissionProgress> = state
    var starts = 0
    var stopped = false

    override fun start() {
      starts++
    }

    override fun stop() {
      stopped = true
    }
  }

  private class RecordingLog : RingLog {
    val types = mutableListOf<RingEventType>()
    val details = mutableListOf<String>()

    override suspend fun record(type: RingEventType, alarmId: Long?, scheduledAt: Instant?, detail: String) {
      types += type
      details += detail
    }
  }
}

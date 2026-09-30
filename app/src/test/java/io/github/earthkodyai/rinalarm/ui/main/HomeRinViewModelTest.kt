package io.github.earthkodyai.rinalarm.ui.main

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.GestureDirector
import io.github.earthkodyai.rinalarm.dialogue.HomeMoments
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.RinSpeaker
import io.github.earthkodyai.rinalarm.testing.FakeLineVoice
import io.github.earthkodyai.rinalarm.testing.FixedTimeSource
import io.github.earthkodyai.rinalarm.testing.MainDispatcherRule
import io.github.earthkodyai.rinalarm.testing.realLineBook
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRinViewModelTest {
  @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

  private var clockMs = 1_000L
  private val moments = HomeMoments()
  private val voice = FakeLineVoice()
  // 19:30 local time: the evening hello.
  private val time = FixedTimeSource(Instant.parse("2026-09-29T19:30:00Z"), ZoneOffset.UTC)

  private fun TestScope.home(book: LineBook = realLineBook()): Pair<HomeRinViewModel, MutableList<Gesture>> {
    val viewModel = HomeRinViewModel(book, { voice }, time, { clockMs }, moments)
    val cues = mutableListOf<Gesture>()
    backgroundScope.launch { viewModel.cues.collect { cues += it } }
    runCurrent()
    return viewModel to cues
  }

  private fun TestScope.untilSaid(line: Line?) {
    advanceTimeBy(RinSpeaker.readingMs(checkNotNull(line)) + 1)
    runCurrent()
  }

  @Test
  fun theAppOpening_getsAHelloForTheTimeOfDay_butNotEachReturnFromAnotherScreen() =
    runTest(main.dispatcher) {
      val (rin, cues) = home()
      rin.onShown()
      runCurrent()
      assertEquals("app.evening", rin.line.value?.pool)
      assertTrue("her page greets with its own gesture", cues.isEmpty())
      untilSaid(rin.line.value)
      assertNull(rin.line.value)

      // The editor and back: no hello.
      rin.onHidden()
      clockMs += 60_000
      rin.onShown()
      runCurrent()
      assertNull(rin.line.value)

      // Away long enough for her greeting wave: a hello again.
      rin.onHidden()
      clockMs += GestureDirector.GREET_AFTER_MS
      rin.onShown()
      runCurrent()
      assertEquals("app.evening", rin.line.value?.pool)
    }

  @Test
  fun leavingTheScreen_stopsHerMidLine() =
    runTest(main.dispatcher) {
      val (rin, _) = home()
      rin.onShown()
      runCurrent()
      rin.onHidden()
      runCurrent()
      assertNull(rin.line.value)
    }

  @Test
  fun aSavedAlarm_andAHeadTap_getTheirLines_withTheirGesturesAsTheStripPlaysThem() =
    runTest(main.dispatcher) {
      val (rin, cues) = home()
      moments.alarmSaved.trySend(Unit)
      runCurrent()
      val saved = checkNotNull(rin.line.value)
      assertEquals("app.alarmset", saved.pool)
      saved.gesture?.let { assertEquals(it.onStrip(), cues.last()) }

      rin.onHeadTap()
      runCurrent()
      assertEquals("tap.head", rin.line.value?.pool)
    }

  @Test
  fun withNoScript_sheStaysQuiet() =
    runTest(main.dispatcher) {
      val (rin, _) = home { _, _, _ -> null }
      rin.onShown()
      rin.onHeadTap()
      runCurrent()
      assertNull(rin.line.value)
    }
}

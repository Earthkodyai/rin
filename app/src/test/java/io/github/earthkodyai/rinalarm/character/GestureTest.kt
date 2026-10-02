package io.github.earthkodyai.rinalarm.character

import java.io.File
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureContractTest {
  /** The page builds one .vrma per entry of this table; a gesture the app sends must exist there, and the other way. */
  @Test
  fun appGestures_matchThePageTable() {
    val table = File("../web/character/src/gestures.json") // unit tests run from the app module
    val pageGestures = Json.parseToJsonElement(table.readText()).jsonObject.keys.toList()

    assertEquals(pageGestures, Gesture.entries.map { it.wire })
  }

  @Test
  fun wireNames_roundTrip() {
    Gesture.entries.forEach { assertEquals(it, Gesture.fromWire(it.wire)) }
    assertNull(Gesture.fromWire("WAVE"))
    assertNull(Gesture.fromWire("idle")) // breathing is not a gesture
  }
}

class GestureDirectorTest {
  @Test
  fun everyMood_hasAGreeting_andIdleGestures_butNeverOneWithHandsOutOfTheStrip() {
    for (mood in Mood.entries) {
      val idle = GestureDirector.IDLE.getValue(mood)
      assertTrue(idle.isNotEmpty())
      // Clap is kept for missions (Phase 3); pout's folded arms fall below the strip's frame.
      for (offFrame in listOf(Gesture.CLAP, Gesture.POUT)) {
        assertNotEquals(offFrame, GestureDirector.GREETING.getValue(mood))
        assertFalse(offFrame in idle)
      }
    }
    assertEquals(Gesture.WAVE, GestureDirector.GREETING[Mood.CHEERFUL])
    assertEquals(Gesture.YAWN, GestureDirector.GREETING[Mood.SLEEPY])
  }

  @Test
  fun greets_onOpen_andAfterFiveMinutesAway_only() {
    val director = GestureDirector(Random(1))
    assertEquals(Gesture.WAVE, director.greetOnShow(Mood.CHEERFUL, awayMs = null))
    assertNull(director.greetOnShow(Mood.CHEERFUL, awayMs = GestureDirector.GREET_AFTER_MS - 1))
    assertEquals(Gesture.YAWN, director.greetOnShow(Mood.SLEEPY, awayMs = GestureDirector.GREET_AFTER_MS))
  }

  @Test
  fun idle_neverRepeatsBackToBack_whenTheMoodHasAChoice() {
    val director = GestureDirector(Random(7))
    var last: Gesture? = null
    repeat(200) {
      val next = director.idle(Mood.CHEERFUL)
      assertTrue(next in GestureDirector.IDLE.getValue(Mood.CHEERFUL))
      assertNotEquals(last, next)
      last = next
    }
    assertEquals(Gesture.HUFF, director.idle(Mood.SULKY)) // the only choice repeats
    assertEquals(Gesture.HUFF, director.idle(Mood.SULKY))
  }

  @Test
  fun idleDelay_isThirtyToSixtySeconds() {
    val director = GestureDirector(Random(3))
    repeat(500) { assertTrue(director.nextIdleDelayMs() in 30_000L..60_000L) }
  }

  @Test
  fun inAGame_onlyHeadAndShoulderGesturesPlay_andIdleOnesNeverMoveHerArms() {
    assertEquals(setOf(Gesture.NOD, Gesture.SHAKE, Gesture.HUFF), Gesture.entries.filter { it.armsStill }.toSet())
    // The stretch the user saw over the pads and away from the cups (G.1).
    assertFalse(Gesture.STRETCH.armsStill)
    val director = GestureDirector(kotlin.random.Random(3))
    repeat(200) {
      for (mood in Mood.entries) director.idleInGame(mood)?.let { assertTrue(it.armsStill) }
    }
  }
}

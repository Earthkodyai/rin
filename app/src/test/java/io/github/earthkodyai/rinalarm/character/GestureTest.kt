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
  fun everyMood_hasAGreeting_andIdleGestures_butNeverAClap() {
    for (mood in Mood.entries) {
      assertNotEquals(Gesture.CLAP, GestureDirector.GREETING.getValue(mood))
      val idle = GestureDirector.IDLE.getValue(mood)
      assertTrue(idle.isNotEmpty())
      assertFalse(Gesture.CLAP in idle) // kept for missions (Phase 3)
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
    assertEquals(Gesture.POUT, director.idle(Mood.SULKY)) // the only choice repeats
    assertEquals(Gesture.POUT, director.idle(Mood.SULKY))
  }

  @Test
  fun idleDelay_isThirtyToSixtySeconds() {
    val director = GestureDirector(Random(3))
    repeat(500) { assertTrue(director.nextIdleDelayMs() in 30_000L..60_000L) }
  }
}

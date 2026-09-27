package io.github.earthkodyai.rinalarm.character

import java.io.File
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodContractTest {
  /** The page draws moods from this table; a mood the app sends must exist there, and the other way round. */
  @Test
  fun appMoods_matchThePageTable() {
    val table = File("../web/character/src/moods.json") // unit tests run from the app module
    val pageMoods = Json.parseToJsonElement(table.readText()).jsonObject.keys.toList()

    assertEquals(pageMoods, Mood.entries.map { it.wire })
  }

  @Test
  fun wireNames_roundTrip() {
    Mood.entries.forEach { assertEquals(it, Mood.fromWire(it.wire)) }
    assertNull(Mood.fromWire("happy")) // a VRM expression, not a mood
    assertNull(Mood.fromWire("SLEEPY"))
  }
}

class DefaultMoodTest {
  @Test
  fun sleepyFrom22To6_cheerfulOtherwise() {
    assertEquals(Mood.CHEERFUL, DefaultMood.at(LocalTime.of(21, 59, 59)))
    assertEquals(Mood.SLEEPY, DefaultMood.at(LocalTime.of(22, 0)))
    assertEquals(Mood.SLEEPY, DefaultMood.at(LocalTime.MIDNIGHT))
    assertEquals(Mood.SLEEPY, DefaultMood.at(LocalTime.of(5, 59, 59)))
    assertEquals(Mood.CHEERFUL, DefaultMood.at(LocalTime.of(6, 0)))
    assertEquals(Mood.CHEERFUL, DefaultMood.at(LocalTime.NOON))
  }
}

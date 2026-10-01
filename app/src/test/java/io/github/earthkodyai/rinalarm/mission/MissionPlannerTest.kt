package io.github.earthkodyai.rinalarm.mission

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPlannerTest {
  private val day = LocalDate.of(2026, 9, 28)
  private val padsReady = mapOf(MissionType.PADS to Readiness.READY)
  private val padsDenied = mapOf(MissionType.PADS to Readiness.NO_PERMISSION)

  @Test
  fun rinPicks_runsAReadyMission() {
    assertEquals(MissionPlan.Run(MissionType.PADS), MissionPlanner.plan(MissionChoice.RinPicks, padsReady, day))
  }

  @Test
  fun rinPicks_withNothingReady_isAPlainDismiss_withTheReasons() {
    assertEquals(MissionPlan.Unavailable("pads=no_permission cups=no_sensor speech=no_sensor"), MissionPlanner.plan(MissionChoice.RinPicks, padsDenied, day))
    // A readiness check that knows nothing about a type counts it as not ready.
    assertEquals(MissionPlan.Unavailable("pads=no_sensor cups=no_sensor speech=no_sensor"), MissionPlanner.plan(MissionChoice.RinPicks, emptyMap(), day))
  }

  @Test
  fun rinPicks_alternatesTheTwoGames_dayByDay() {
    val games = padsReady + (MissionType.CUPS to Readiness.READY)
    val picks = (0L..3L).map { (MissionPlanner.plan(MissionChoice.RinPicks, games, day.plusDays(it)) as MissionPlan.Run).type }
    assertEquals(setOf(MissionType.PADS, MissionType.CUPS), picks.toSet())
    assertTrue(picks.zipWithNext().all { (a, b) -> a != b })
  }

  @Test
  fun storedWalkAndQr_readAsRinPicks() {
    // Walking was removed in 3.3, the QR sticker in UX.7 (D31).
    assertEquals(MissionChoice.RinPicks, MissionChoice.fromStored("walk"))
    assertEquals(MissionChoice.RinPicks, MissionChoice.fromStored("qr"))
    assertNull(MissionType.fromStored("qr"))
    assertEquals(MissionChoice.Only(MissionType.PADS), MissionChoice.fromStored("pads"))
  }

  @Test
  fun none_isAlwaysAPlainDismiss() {
    assertEquals(MissionPlan.Unavailable("chosen_none"), MissionPlanner.plan(MissionChoice.None, padsReady, day))
  }

  @Test
  fun only_runsItsMission_orExplainsWhyNot() {
    val pads = MissionChoice.Only(MissionType.PADS)
    assertEquals(MissionPlan.Run(MissionType.PADS), MissionPlanner.plan(pads, padsReady, day))
    assertEquals(MissionPlan.Unavailable("pads=no_permission cups=no_sensor speech=no_sensor"), MissionPlanner.plan(pads, padsDenied, day))
  }

  @Test
  fun rotate_keepsOnePickAllDay_andMovesOnEachDay() {
    val options = listOf("pads", "cups", "speak")
    val week = (0L..6L).map { MissionPlanner.rotate(options, day.plusDays(it)) }

    assertEquals(MissionPlanner.rotate(options, day), MissionPlanner.rotate(options, day))
    assertTrue(week.zipWithNext().all { (a, b) -> a != b })
    assertEquals(options.toSet(), week.toSet())
  }

  @Test
  fun rotate_handlesDaysBeforeTheEpoch() {
    assertEquals("b", MissionPlanner.rotate(listOf("a", "b"), LocalDate.ofEpochDay(-1)))
  }
}

package io.github.earthkodyai.rinalarm.mission

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPlannerTest {
  private val day = LocalDate.of(2026, 9, 28)
  private val walkReady = mapOf(MissionType.WALK to Readiness.READY)
  private val walkDenied = mapOf(MissionType.WALK to Readiness.NO_PERMISSION)

  @Test
  fun rinPicks_runsAReadyMission() {
    assertEquals(MissionPlan.Run(MissionType.WALK), MissionPlanner.plan(MissionChoice.RinPicks, walkReady, day))
  }

  @Test
  fun rinPicks_withNothingReady_isAPlainDismiss_withTheReasons() {
    assertEquals(
      MissionPlan.Unavailable("walk=no_permission"),
      MissionPlanner.plan(MissionChoice.RinPicks, walkDenied, day),
    )
    // A readiness check that knows nothing about a type counts it as not ready.
    assertEquals(MissionPlan.Unavailable("walk=no_sensor"), MissionPlanner.plan(MissionChoice.RinPicks, emptyMap(), day))
  }

  @Test
  fun none_isAlwaysAPlainDismiss() {
    assertEquals(MissionPlan.Unavailable("chosen_none"), MissionPlanner.plan(MissionChoice.None, walkReady, day))
  }

  @Test
  fun only_runsItsMission_orExplainsWhyNot() {
    val walk = MissionChoice.Only(MissionType.WALK)
    assertEquals(MissionPlan.Run(MissionType.WALK), MissionPlanner.plan(walk, walkReady, day))
    assertEquals(MissionPlan.Unavailable("walk=no_permission"), MissionPlanner.plan(walk, walkDenied, day))
  }

  @Test
  fun rotate_keepsOnePickAllDay_andMovesOnEachDay() {
    val options = listOf("qr", "speak", "walk")
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

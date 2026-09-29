package io.github.earthkodyai.rinalarm.mission

import java.time.LocalDate

/** The mission one ring runs, decided when it starts ringing (so the notification can leave out Dismiss). */
sealed interface MissionPlan {
  /** @property switchedFrom the mission the alarm asked for, when it was not ready and Rin picked another. */
  data class Run(val type: MissionType, val switchedFrom: MissionType? = null) : MissionPlan

  /** No mission: a plain Dismiss button. [reason] goes to the ring log (MISSION_UNAVAILABLE). */
  data class Unavailable(val reason: String) : MissionPlan
}

/** Pure rules for [MissionPlan], unit-tested; the Android readiness checks are in AndroidMissionReadiness. */
object MissionPlanner {
  fun plan(choice: MissionChoice, readiness: Map<MissionType, Readiness>, day: LocalDate): MissionPlan {
    val ready = MissionType.offeredEntries.filter { readiness[it] == Readiness.READY }
    return when (choice) {
      MissionChoice.None -> MissionPlan.Unavailable("chosen_none")
      MissionChoice.RinPicks ->
        if (ready.isEmpty()) MissionPlan.Unavailable(reasons(readiness)) else MissionPlan.Run(rotate(ready, day))
      is MissionChoice.Only ->
        when {
          choice.type in ready -> MissionPlan.Run(choice.type)
          // 08-error-handling: a mission that cannot run (camera denied…) is swapped for one that can.
          ready.isNotEmpty() -> MissionPlan.Run(rotate(ready, day), switchedFrom = choice.type)
          else -> MissionPlan.Unavailable(reasons(readiness))
        }
    }
  }

  /**
   * The same pick all day (a snooze or a re-ring keeps the mission), a different one the next day whenever more than
   * one is ready: consecutive days step through [options] in order.
   */
  fun <T> rotate(options: List<T>, day: LocalDate): T = options[Math.floorMod(day.toEpochDay(), options.size.toLong()).toInt()]

  private fun reasons(readiness: Map<MissionType, Readiness>): String =
    MissionType.offeredEntries.joinToString(" ") { "${it.stored}=${(readiness[it] ?: Readiness.NO_SENSOR).reason}" }
}

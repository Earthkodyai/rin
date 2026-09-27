package io.github.earthkodyai.rinalarm.alarm.log

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Progress of the Phase 1 exit check (rules chosen by the user in 1.5): [TARGET] calendar days in a row, each with at
 * least one real ring, where every real ring that day fired at most [MAX_LATE] late and started its tone. A bad ring
 * or a day without any ring starts the count again. Test rings are left out. A ring belongs to the local date it was
 * scheduled for.
 *
 * @property nights good days in a row, counted back from today (or from yesterday while today has no ring yet).
 * @property breaker what ended the run before those [nights]; null when the log has nothing earlier.
 */
data class ReliabilityRun(val nights: Int, val breaker: Breaker?) {
  val passed: Boolean
    get() = nights >= TARGET

  data class Breaker(val day: LocalDate, val reason: Reason, val alarmId: Long?)

  enum class Reason {
    MISSED,
    /** Fired more than [MAX_LATE] late. */
    LATE,
    /** Fired, but the tone never started. */
    NO_SOUND,
    /** The ring service or the tone failed. */
    FAILED,
    /** Fired without a stored time (its pending row was gone), so lateness is unknown. */
    NO_TIME,
    /** No real ring that day: an alarm was not set, or the phone was off long enough to skip it. */
    NO_ALARM,
  }

  companion object {
    const val TARGET = 14
    val MAX_LATE: Duration = Duration.ofSeconds(60)

    /** Why [ring] fails the run, or null when it passes. */
    fun verdict(ring: RingSummary): Reason? {
      val late = ring.late
      return when {
        ring.outcome == RingOutcome.MISSED -> Reason.MISSED
        ring.outcome == RingOutcome.FAILED -> Reason.FAILED
        late == null -> Reason.NO_TIME
        late > MAX_LATE -> Reason.LATE
        // An overlapped ring had no tone of its own: the ring already sounding covered it.
        ring.toSound == null && ring.outcome != RingOutcome.OVERLAPPED -> Reason.NO_SOUND
        else -> null
      }
    }

    /** [rings] in any order, as many as the log holds; [today] in [zone]. */
    fun from(rings: List<RingSummary>, today: LocalDate, zone: ZoneId): ReliabilityRun {
      val byDay = rings.filter { !it.isTest }.groupBy { (it.scheduledAt ?: it.firedAt).atZone(zone).toLocalDate() }
      val first = byDay.keys.minOrNull() ?: return ReliabilityRun(0, null)
      var day = if (today in byDay) today else today.minusDays(1)
      var nights = 0
      while (!day.isBefore(first)) {
        val dayRings = byDay[day] ?: return ReliabilityRun(nights, Breaker(day, Reason.NO_ALARM, null))
        for (ring in dayRings.sortedBy { it.firedAt }) {
          val reason = verdict(ring) ?: continue
          return ReliabilityRun(nights, Breaker(day, reason, ring.alarmId))
        }
        nights++
        day = day.minusDays(1)
      }
      return ReliabilityRun(nights, null)
    }
  }
}

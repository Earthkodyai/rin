package io.github.earthkodyai.rinalarm.alarm.log

import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.data.db.RingEventDao
import io.github.earthkodyai.rinalarm.data.db.RingEventEntity
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One ring log row, parsed. [type] is null for a name this build does not know (the log only ever grows). */
data class RingEvent(
  val id: Long,
  val at: Instant,
  val type: RingEventType?,
  val alarmId: Long?,
  val scheduledAt: Instant?,
  val detail: String,
)

fun RingEventEntity.toRingEvent(): RingEvent =
  RingEvent(
    id = id,
    at = Instant.ofEpochMilli(atMillis),
    type = RingEventType.entries.find { it.name == type },
    alarmId = alarmId,
    scheduledAt = scheduledAtMillis?.let(Instant::ofEpochMilli),
    detail = detail,
  )

/** How a ring ended, as far as the log knows. */
enum class RingOutcome {
  DISMISSED,
  SNOOZED,
  AUTO_STOPPED,
  /** Rang while another alarm was already ringing; that ring covered it. */
  OVERLAPPED,
  /** The tone or the ring service failed. */
  FAILED,
  /** Found more than 15 minutes overdue: never rang, the user got a notification. */
  MISSED,
  /** No ending logged yet: still ringing, or the process died. */
  UNKNOWN,
}

/**
 * One ring for the Diagnostics list.
 *
 * @property late how long after [scheduledAt] the alarm fired (null without a stored time, or when missed).
 * @property toSound from firing to the tone starting (null when it never started).
 */
data class RingSummary(
  val alarmId: Long,
  val scheduledAt: Instant?,
  val firedAt: Instant,
  val late: Duration?,
  val toSound: Duration?,
  val outcome: RingOutcome,
  val isTest: Boolean,
)

/** The newest rings for Diagnostics. */
interface RingHistoryRepository {
  val recentRings: Flow<List<RingSummary>>
}

class RoomRingHistory @Inject constructor(private val dao: RingEventDao) : RingHistoryRepository {
  override val recentRings: Flow<List<RingSummary>> =
    dao.observeRingEvents(EVENT_WINDOW).map { rows -> RingHistory.summarize(rows.map { it.toRingEvent() }, RECENT_RINGS) }

  private companion object {
    /** Enough rows for the last rings with their endings; a ring whose FIRED row fell outside is simply not shown. */
    const val EVENT_WINDOW = 200
    const val RECENT_RINGS = 7
  }
}

object RingHistory {
  private val ENDINGS =
    mapOf(
      RingEventType.DISMISSED to RingOutcome.DISMISSED,
      RingEventType.SNOOZED to RingOutcome.SNOOZED,
      RingEventType.AUTO_STOPPED to RingOutcome.AUTO_STOPPED,
      RingEventType.OVERLAP to RingOutcome.OVERLAPPED,
      RingEventType.RING_FAIL to RingOutcome.FAILED,
      RingEventType.FGS_FAIL to RingOutcome.FAILED,
    )

  /**
   * Pairs every FIRED row with the RING_START and ending logged after it for the same alarm, up to that alarm's next
   * FIRED. MISSED rows become rings of their own. Returns the newest [limit], newest first; [events] may be in any
   * order.
   */
  fun summarize(events: List<RingEvent>, limit: Int): List<RingSummary> {
    val ordered = events.sortedBy { it.id }
    val rings = mutableListOf<RingSummary>()
    for ((index, event) in ordered.withIndex()) {
      val alarmId = event.alarmId ?: continue
      val isTest = AlarmEngine.TEST_MARK in event.detail.split(' ')
      when (event.type) {
        RingEventType.MISSED ->
          rings += RingSummary(alarmId, event.scheduledAt, event.at, null, null, RingOutcome.MISSED, isTest)
        RingEventType.FIRED -> {
          var toSound: Duration? = null
          var outcome = RingOutcome.UNKNOWN
          for (next in ordered.subList(index + 1, ordered.size)) {
            if (next.alarmId != alarmId) continue
            if (next.type == RingEventType.FIRED) break
            if (next.type == RingEventType.RING_START && toSound == null) toSound = Duration.between(event.at, next.at)
            val ending = ENDINGS[next.type]
            if (ending != null) {
              outcome = ending
              break
            }
          }
          val late = event.scheduledAt?.let { Duration.between(it, event.at) }
          rings += RingSummary(alarmId, event.scheduledAt, event.at, late, toSound, outcome, isTest)
        }
        else -> Unit
      }
    }
    return rings.asReversed().take(limit)
  }
}

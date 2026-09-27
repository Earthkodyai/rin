package io.github.earthkodyai.rinalarm.alarm.log

import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.AUTO_STOPPED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.DISMISSED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.FGS_FAIL
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.FIRED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.MISSED
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.OVERLAP
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.RING_START
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType.SNOOZED
import io.github.earthkodyai.rinalarm.data.db.RingEventEntity
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingHistoryTest {
  private val t0 = Instant.parse("2026-09-28T00:00:00Z")
  private var nextId = 1L

  private fun event(type: RingEventType, alarmId: Long, atMs: Long, scheduledMs: Long? = null, detail: String = "") =
    RingEvent(nextId++, t0.plusMillis(atMs), type, alarmId, scheduledMs?.let { t0.plusMillis(it) }, detail)

  @Test
  fun firedRing_getsLateness_soundLatency_andOutcome() {
    val events =
      listOf(
        event(FIRED, 1, atMs = 52, scheduledMs = 0),
        event(RING_START, 1, atMs = 950),
        event(DISMISSED, 1, atMs = 20_000),
      )

    val ring = RingHistory.summarize(events, 7).single()

    assertEquals(Duration.ofMillis(52), ring.late)
    assertEquals(Duration.ofMillis(898), ring.toSound)
    assertEquals(RingOutcome.DISMISSED, ring.outcome)
    assertEquals(t0, ring.scheduledAt)
  }

  @Test
  fun snoozedRing_andItsSnooze_areTwoRings_newestFirst() {
    val events =
      listOf(
        event(FIRED, 1, atMs = 10, scheduledMs = 0),
        event(SNOOZED, 1, atMs = 5_000),
        event(FIRED, 1, atMs = 300_020, scheduledMs = 300_000),
        event(AUTO_STOPPED, 1, atMs = 1_200_000),
      )

    val rings = RingHistory.summarize(events, 7)

    assertEquals(listOf(RingOutcome.AUTO_STOPPED, RingOutcome.SNOOZED), rings.map { it.outcome })
  }

  @Test
  fun eventsOfOtherAlarms_doNotEndARing() {
    val events =
      listOf(
        event(FIRED, 1, atMs = 0, scheduledMs = 0),
        event(FIRED, 2, atMs = 5, scheduledMs = 0),
        event(OVERLAP, 2, atMs = 6),
        event(DISMISSED, 1, atMs = 9_000),
      )

    val byAlarm = RingHistory.summarize(events, 7).associate { it.alarmId to it.outcome }

    assertEquals(mapOf(1L to RingOutcome.DISMISSED, 2L to RingOutcome.OVERLAPPED), byAlarm)
  }

  @Test
  fun missed_andFailed_andStillRinging() {
    val events =
      listOf(
        event(MISSED, 1, atMs = 3_600_000, scheduledMs = 0),
        event(FIRED, 2, atMs = 3_700_000, scheduledMs = 3_700_000),
        event(FGS_FAIL, 2, atMs = 3_700_001),
        event(FIRED, 3, atMs = 3_800_000, scheduledMs = 3_800_000),
      )

    val rings = RingHistory.summarize(events, 7)

    assertEquals(listOf(RingOutcome.UNKNOWN, RingOutcome.FAILED, RingOutcome.MISSED), rings.map { it.outcome })
    assertNull(rings.last().late)
  }

  @Test
  fun testRings_areMarked_andTheLimitKeepsTheNewest() {
    val events = (0 until 10).map { event(FIRED, it.toLong(), atMs = it * 1000L, scheduledMs = it * 1000L) } +
      event(FIRED, 99, atMs = 20_000, scheduledMs = 20_000, detail = "screen=off snoozeCount=0 test=true")

    val rings = RingHistory.summarize(events.shuffled(), 7)

    assertEquals(7, rings.size)
    assertEquals(99L, rings.first().alarmId)
    assertTrue(rings.first().isTest)
    assertTrue(rings.drop(1).none { it.isTest })
  }

  @Test
  fun unknownEventNames_areSkipped() {
    val entity = RingEventEntity(1, 0, "SOMETHING_NEW", 1, null, "")
    assertNull(entity.toRingEvent().type)
    assertTrue(RingHistory.summarize(listOf(entity.toRingEvent()), 7).isEmpty())
  }
}

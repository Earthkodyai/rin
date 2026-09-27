package io.github.earthkodyai.rinalarm.alarm.schedule

import java.time.DayOfWeek

/**
 * The weekdays an alarm repeats on, stored as a 7-bit mask (Monday = bit 0, ISO order). An empty set means a
 * one-shot alarm.
 */
@JvmInline
value class RepeatDays private constructor(val mask: Int) {
  val isOneShot: Boolean
    get() = mask == 0

  val days: List<DayOfWeek>
    get() = DayOfWeek.entries.filter { it in this }

  operator fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0

  fun toggle(day: DayOfWeek): RepeatDays = RepeatDays(mask xor bit(day))

  override fun toString(): String = if (isOneShot) "RepeatDays(once)" else "RepeatDays($days)"

  companion object {
    private const val ALL_BITS = 0x7F

    val NONE = RepeatDays(0)
    val EVERY_DAY = RepeatDays(ALL_BITS)
    val WEEKDAYS = of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val WEEKEND = of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun of(vararg days: DayOfWeek): RepeatDays = RepeatDays(days.fold(0) { mask, day -> mask or bit(day) })

    fun fromMask(mask: Int): RepeatDays {
      require(mask in 0..ALL_BITS) { "Repeat mask out of range: $mask" }
      return RepeatDays(mask)
    }

    private fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
  }
}

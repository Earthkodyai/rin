package io.github.earthkodyai.rinalarm.dialogue

import java.time.LocalDate
import kotlin.random.Random

/**
 * Picks Rin's lines by the pool rules of script-bible §4 (task 4.2), keeping nothing between days (D22: no line
 * history is stored).
 *
 * - [PoolUse.DAILY]: the date decides. Each pool has a fixed shuffled order and moves one place a day, so a line comes
 *   back after as many days as its pool has lines (at least 7). A pool that mixes into a shared one (game.intro.pads
 *   into game.intro) follows a day pattern that every pool mixing into it shares: on the shared pool's days all of them
 *   say the same line of it, so a shared line keeps its full cycle even when the game changes every day (Rin picks).
 *   The pool's own lines count only their own days, so a small pool (after.meal.late) still waits as long as it can.
 * - [PoolUse.SESSION]: a shuffled bag per morning, emptied before any line comes back.
 * - [PoolUse.EVENT]: at random, never the line picked last time (in memory only: a fresh process may repeat it).
 *
 * Main thread only.
 */
class LinePicker(private val script: Script, private val random: Random = Random.Default) {
  private val orders = mutableMapOf<String, List<Line>>()
  private var morning: Any? = null
  private val bags = mutableMapOf<String, ArrayDeque<Line>>()
  private val lastSaid = mutableMapOf<String, String>()

  /** A line from [pool] by its rule, or null when the pool has none. [morning] keys the session bags. */
  fun pick(pool: String, day: LocalDate, morning: Any): Line? =
    when (script.pools[pool]?.use ?: PoolUse.EVENT) {
      PoolUse.DAILY -> daily(pool, day)
      PoolUse.SESSION -> session(pool, morning)
      PoolUse.EVENT -> event(pool)
    }

  fun daily(pool: String, day: LocalDate): Line? {
    val own = order(pool)
    val shared = script.pools[pool]?.mix
    val base = shared?.let(::order).orEmpty()
    val d = day.toEpochDay()
    if (base.isEmpty()) return own.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(d, it.size.toLong()).toInt()] }
    // The widest pool mixing into the shared one sets how many of each [period] days are the mixing pools' days.
    val m = script.pools.filterValues { it.mix == shared }.keys.maxOf { script.pool(it).size }.toLong()
    val b = base.size.toLong()
    val period = b + m
    val cycle = Math.floorDiv(d, period)
    val pos = Math.floorMod(d, period)
    // Mixing days spread evenly over the period: day i is one when (i + 1) * m / period steps up.
    val mixBefore = pos * m / period
    val mixDay = (pos + 1) * m / period > mixBefore
    return when {
      mixDay && own.isNotEmpty() -> own[Math.floorMod(cycle * m + mixBefore, own.size.toLong()).toInt()]
      else -> base[Math.floorMod(cycle * b + pos - mixBefore, b).toInt()]
    }
  }

  fun session(pool: String, morning: Any): Line? {
    if (morning != this.morning) {
      this.morning = morning
      bags.clear()
    }
    val lines = script.pool(pool)
    if (lines.isEmpty()) return null
    val bag = bags.getOrPut(pool) { ArrayDeque() }
    if (bag.isEmpty()) {
      val fresh = lines.shuffled(random).toMutableList()
      // A new round of the bag never starts with the line that ended the last one.
      if (fresh.size > 1 && fresh.first().id == lastSaid[pool]) fresh.add(fresh.removeAt(0))
      bag.addAll(fresh)
    }
    return bag.removeFirst().also { lastSaid[pool] = it.id }
  }

  fun event(pool: String): Line? {
    val lines = script.pool(pool)
    if (lines.isEmpty()) return null
    val choices = lines.filter { it.id != lastSaid[pool] }.ifEmpty { lines }
    return choices[random.nextInt(choices.size)].also { lastSaid[pool] = it.id }
  }

  /** The pool's fixed daily order: its lines shuffled by a seed from the pool's name, the same on every phone. */
  private fun order(pool: String): List<Line> =
    orders.getOrPut(pool) {
      val lines = script.pool(pool).toMutableList()
      var seed = pool.fold(FNV_OFFSET) { h, c -> (h xor c.code.toLong()) * FNV_PRIME }
      for (i in lines.lastIndex downTo 1) {
        seed = splitMix(seed)
        val j = Math.floorMod(seed ushr 1, (i + 1).toLong()).toInt()
        lines[i] = lines[j].also { lines[j] = lines[i] }
      }
      lines
    }

  private companion object {
    const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    const val FNV_PRIME = 1099511628211L

    /** SplitMix64: a fixed, well-known mixer, so the daily orders never change with a library update. */
    fun splitMix(x: Long): Long {
      var z = x + -7046029254386353131L // 0x9e3779b97f4a7c15
      z = (z xor (z ushr 30)) * -4658895280553007687L // 0xbf58476d1ce4e5b9
      z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94d049bb133111eb
      return z xor (z ushr 31)
    }
  }
}

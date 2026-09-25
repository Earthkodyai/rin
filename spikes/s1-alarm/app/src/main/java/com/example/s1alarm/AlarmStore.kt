package com.example.s1alarm

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG = "S1"

data class Alarm(val id: Int, val triggerAt: Long, val label: String, val ringSec: Int)

/** Device-protected storage so LOCKED_BOOT_COMPLETED can read it before the first unlock. */
private fun Context.de(): Context = createDeviceProtectedStorageContext()

object AlarmStore {
  private const val PREFS = "s1_alarms"
  private const val KEY = "alarms"

  fun all(ctx: Context): List<Alarm> =
    ctx.de().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getStringSet(KEY, emptySet())!!
      .mapNotNull { s ->
        val p = s.split('|')
        if (p.size == 4) Alarm(p[0].toInt(), p[1].toLong(), p[2], p[3].toInt()) else null
      }
      .sortedBy { it.triggerAt }

  fun put(ctx: Context, a: Alarm) = save(ctx, all(ctx).filter { it.id != a.id } + a)

  fun remove(ctx: Context, id: Int) = save(ctx, all(ctx).filter { it.id != id })

  fun clear(ctx: Context) = save(ctx, emptyList())

  private fun save(ctx: Context, list: List<Alarm>) {
    ctx.de().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putStringSet(KEY, list.map { "${it.id}|${it.triggerAt}|${it.label}|${it.ringSec}" }.toSet())
      .commit()
  }
}

/** Append-only CSV: time,event,id,label,scheduled_ms,actual_ms,delta_ms,info */
object RingLog {
  private const val FILE = "ringlog.csv"
  private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  fun file(ctx: Context) = File(ctx.de().filesDir, FILE)

  @Synchronized
  fun log(ctx: Context, event: String, a: Alarm?, info: String = "") {
    val now = System.currentTimeMillis()
    val delta = a?.let { now - it.triggerAt }
    val line = listOf(
      fmt.format(Date(now)), event, a?.id ?: "", a?.label ?: "",
      a?.triggerAt ?: "", now, delta ?: "", info.replace(',', ';'),
    ).joinToString(",")
    Log.i(TAG, line)
    runCatching { file(ctx).appendText(line + "\n") }
  }

  fun tail(ctx: Context, n: Int): List<String> =
    runCatching { file(ctx).readLines().takeLast(n).reversed() }.getOrDefault(emptyList())
}

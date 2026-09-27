package io.github.earthkodyai.rinalarm.setup

import io.github.earthkodyai.rinalarm.alarm.log.ReliabilityRun
import io.github.earthkodyai.rinalarm.alarm.log.RingSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "Copy report" text: plain English and fixed formats (not localized), so it pastes cleanly into a bug report.
 * It never leaves the phone by itself; the user decides where to paste it.
 */
object DiagnosticsReport {
  private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  fun build(
    status: DeviceStatus,
    checks: List<CheckResult>,
    snapshot: String,
    rings: List<RingSummary>,
    run: ReliabilityRun,
    now: Instant,
    zone: ZoneId,
  ): String = buildString {
    appendLine("RinAlarm ${status.appVersion} diagnostics, ${TIME.format(now.atZone(zone))} ($zone)")
    appendLine("${status.device}, Android ${status.androidRelease} (SDK ${status.sdk})")
    appendLine()
    appendLine("Checks:")
    for (check in checks) appendLine("- ${check.id}: ${check.severity}")
    appendLine("Alarm volume ${status.alarmVolume}/${status.alarmVolumeMax}, battery saver ${onOff(status.powerSaveOn)}")
    appendLine("Now: $snapshot")
    appendLine()
    append("Reliability run: ${run.nights}/${ReliabilityRun.TARGET} days")
    run.breaker?.let { append(", reset on ${it.day} by ${it.reason}" + (it.alarmId?.let { id -> " (alarm=$id)" } ?: "")) }
    appendLine()
    appendLine()
    appendLine("Recent rings (newest first):")
    if (rings.isEmpty()) appendLine("- none")
    for (ring in rings) {
      val scheduled = ring.scheduledAt?.let { TIME.format(it.atZone(zone)) } ?: "no stored time"
      val late = ring.late?.let { " late=${it.toMillis()}ms" } ?: ""
      val sound = ring.toSound?.let { " toSound=${it.toMillis()}ms" } ?: ""
      val test = if (ring.isTest) " test" else ""
      appendLine("- $scheduled alarm=${ring.alarmId} ${ring.outcome}$late$sound$test")
    }
  }

  private fun onOff(on: Boolean) = if (on) "on" else "off"
}

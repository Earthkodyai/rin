package io.github.earthkodyai.rinalarm.data

import io.github.earthkodyai.rinalarm.alarm.engine.AlarmWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * G.1 moved "No pouting" from Settings onto each alarm (the scold switch). Someone who had it on keeps a calm Rin: every
 * alarm gets scold off, and so does the next new one. Runs at each start until it has run once; the flag goes last, so
 * a start cut short just runs it again.
 */
class LegacyPoutOff @Inject constructor(private val settings: AppSettings, private val alarms: AlarmRepository, private val writer: AlarmWriter) {
  suspend fun run() {
    if (!settings.legacyPoutOff()) return
    alarms.alarms.first().filter { it.scold }.forEach { writer.setScold(it.id, false) }
    settings.setLastScold(false)
    settings.clearLegacyPoutOff()
  }
}

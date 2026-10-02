package io.github.earthkodyai.rinalarm.testing

import io.github.earthkodyai.rinalarm.character.Speaking
import io.github.earthkodyai.rinalarm.dialogue.Line
import io.github.earthkodyai.rinalarm.dialogue.LineBook
import io.github.earthkodyai.rinalarm.dialogue.LinePicker
import io.github.earthkodyai.rinalarm.dialogue.LineVoice
import io.github.earthkodyai.rinalarm.dialogue.Script
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Rin's real script (assets/dialogue/lines.json), read the way the app reads it. */
val realScript: Script by lazy { Script.parse(File("src/main/assets/dialogue/lines.json").readText()) }

/** A book over the real script, with a fixed seed so session and event picks repeat run to run. */
fun realLineBook(seed: Int = 1): LineBook {
  val picker = LinePicker(realScript, Random(seed))
  return object : LineBook {
    override suspend fun pick(pool: String, day: java.time.LocalDate, morning: Any): Line? = picker.pick(pool, day, morning)

    override suspend fun line(id: String): Line? = picker.line(id)
  }
}

/** No script: Rin stays quiet (what the screens do when lines.json cannot be read). */
val quietLineBook = LineBook { _, _, _ -> null }

/** Clips for the line ids in [clips], each [clipMs] long on the test clock. */
class FakeLineVoice(var clips: Set<String> = emptySet(), private val clipMs: Long = 1_000) : LineVoice {
  override val speaking = MutableStateFlow<Speaking?>(null)
  val played = mutableListOf<String>()
  var released = false

  override fun hasClip(line: Line): Boolean = line.id in clips

  override suspend fun play(line: Line): Boolean {
    played += line.id
    delay(clipMs)
    return true
  }

  override fun release() {
    released = true
  }
}

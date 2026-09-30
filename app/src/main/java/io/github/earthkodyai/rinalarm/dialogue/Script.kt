package io.github.earthkodyai.rinalarm.dialogue

import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One of Rin's lines (assets/dialogue/lines.json, task 4.1). [text] is what the screen shows (`~` included); the clip
 * recorded in 4.3 is named by [id]. [emotion] and [gesture] are hers while she says it.
 */
data class Line(val id: String, val pool: String, val text: String, val emotion: Mood?, val gesture: Gesture?)

/** How a pool's lines are chosen (script-bible §4). */
enum class PoolUse {
  /** At most once a day, and not again within 7 days: the date picks it. */
  DAILY,
  /** Several times in one morning, never the same one twice until all have been said. */
  SESSION,
  /** Now and then (a snooze, a late ring, the app opening): never the one said last time. */
  EVENT,
}

/** [mix]: a pool whose lines are drawn together with this one's (game.intro for game.intro.pads). */
data class PoolRule(val use: PoolUse, val mix: String? = null)

/** Rin's whole script: every line by pool, and how each pool is used. */
class Script(val lines: List<Line>, val pools: Map<String, PoolRule>) {
  private val byPool = lines.groupBy { it.pool }

  /** A pool's lines in file order; empty for a pool with none. */
  fun pool(name: String): List<Line> = byPool[name].orEmpty()

  companion object {
    const val ASSET = "dialogue/lines.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class PoolDto(val use: String, val mix: String? = null)

    @Serializable
    private data class LineDto(
      val id: String,
      val pool: String,
      val text: String,
      val emotion: String? = null,
      val gesture: String? = null,
    )

    @Serializable private data class FileDto(val pools: Map<String, PoolDto>, val lines: List<LineDto>)

    /** Throws on a file the app cannot use: an unknown pool use. Unknown moods and gestures are dropped. */
    fun parse(text: String): Script {
      val file = json.decodeFromString<FileDto>(text)
      val pools = file.pools.mapValues { (_, p) -> PoolRule(PoolUse.valueOf(p.use.uppercase()), p.mix) }
      val lines =
        file.lines.map {
          Line(it.id, it.pool, it.text, it.emotion?.let(Mood::fromWire), it.gesture?.let(Gesture::fromWire))
        }
      return Script(lines, pools)
    }
  }
}

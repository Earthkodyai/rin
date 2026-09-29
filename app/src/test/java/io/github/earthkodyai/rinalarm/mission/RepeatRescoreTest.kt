package io.github.earthkodyai.rinalarm.mission

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Re-judges a lab replay (RepeatLabActivity) on the PC, without the phone: every combination of [MatchRules] on the
 * words and confidences Vosk heard (task 3.5). Skipped in normal runs. With results pulled from the phone:
 *   ./gradlew testDebugUnitTest --tests '*RepeatRescoreTest' -Prepeat.results=<abs path>[,<abs path>...]
 * (one file per grammar: decoys on and off). Prints the grid, and the pick by the rule frozen before the dev data
 * (docs/spikes/3.5-repeat-after-rin.md): no false accept on the negatives; then the most "say" tries accepted; ties
 * go to the stricter rules (higher coverage, then higher confidence, then decoys on).
 */
class RepeatRescoreTest {
  private val pool = RepeatSentences.parse(File("src/main/assets/repeat/sentences.json").readText()).associateBy { it.id }

  private data class Try(val id: String, val kind: String, val target: String, val cond: String, val decoys: Boolean, val heard: Heard)

  private data class Score(val rules: MatchRules, val sayOk: Int, val says: Int, val falseAccepts: Int, val negatives: Int, val byCond: Map<String, Pair<Int, Int>>)

  @Test
  fun rescore() {
    val paths = System.getProperty("repeat.results").orEmpty()
    assumeTrue("no -Prepeat.results", paths.isNotBlank())
    val tries = paths.split(',').filter { it.isNotBlank() }.flatMap { load(File(it.trim())) }
    val scores =
      tries.groupBy { it.decoys }.flatMap { (decoys, set) ->
        CONFS.flatMap { conf -> COVERAGES.map { cov -> score(set, MatchRules(minConf = conf, minCoverage = cov, decoys = decoys)) } }
      }
    println("decoys minConf minCov | say ok      | false accepts | hand / lying / far")
    scores.forEach { println(line(it)) }
    val pick =
      scores
        .filter { it.falseAccepts == 0 }
        .sortedWith(
          compareByDescending<Score> { it.sayOk }
            .thenByDescending { it.rules.minCoverage }
            .thenByDescending { it.rules.minConf }
            .thenByDescending { it.rules.decoys }
        )
        .firstOrNull()
    println("PICK: " + (pick?.let(::line) ?: "none with zero false accepts"))
    val default = scores.firstOrNull { it.rules == MatchRules() }
    println("CURRENT (MatchRules()): " + (default?.let(::line) ?: "not in the grid"))
    tries.filter { it.kind != "say" }.forEach { t ->
      val m = RepeatMatcher.match(pool.getValue(t.target), t.heard, pick?.rules ?: MatchRules())
      println("  neg ${t.id} ${t.kind} decoys=${t.decoys} ${m.matched}/${m.of} heard=${t.heard.words.joinToString(" ") { "${it.word}:${"%.2f".format(it.conf)}" }}")
    }
  }

  private fun score(set: List<Try>, rules: MatchRules): Score {
    val says = set.filter { it.kind == "say" }
    val negatives = set.filter { it.kind != "say" }
    fun ok(t: Try) = RepeatMatcher.match(pool.getValue(t.target), t.heard, rules).accepted
    return Score(
      rules,
      says.count(::ok),
      says.size,
      negatives.count(::ok),
      negatives.size,
      says.groupBy { it.cond }.mapValues { (_, v) -> v.count(::ok) to v.size },
    )
  }

  private fun line(s: Score): String {
    val cond = listOf("hand", "lying", "far").joinToString(" / ") { c -> s.byCond[c]?.let { "${it.first}/${it.second}" } ?: "-" }
    return "%-6s %-7.2f %-6.2f | %2d/%2d %5.1f%% | %2d/%2d         | %s".format(
      s.rules.decoys, s.rules.minConf, s.rules.minCoverage, s.sayOk, s.says, 100.0 * s.sayOk / maxOf(s.says, 1), s.falseAccepts, s.negatives, cond,
    )
  }

  private fun load(file: File): List<Try> =
    file.readLines().filter { it.isNotBlank() }.map { line ->
      val o = Json.parseToJsonElement(line).jsonObject
      Try(
        o.getValue("id").jsonPrimitive.content,
        o.getValue("kind").jsonPrimitive.content,
        o.getValue("target").jsonPrimitive.content,
        o.getValue("cond").jsonPrimitive.content,
        o.getValue("decoys").jsonPrimitive.boolean,
        Heard(o.getValue("words").jsonArray.map { w -> HeardWord(w.jsonObject.getValue("w").jsonPrimitive.content, w.jsonObject.getValue("c").jsonPrimitive.float) }),
      )
    }

  private companion object {
    val CONFS = listOf(0f, 0.3f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f)
    val COVERAGES = listOf(0.5f, 0.6f, 0.7f, 0.75f, 0.8f, 0.9f, 1f)
  }
}

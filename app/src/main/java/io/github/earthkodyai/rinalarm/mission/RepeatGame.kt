package io.github.earthkodyai.rinalarm.mission

import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One sentence Rin says and the user repeats (task 3.5). [id] names its clip and its log entries, so ids never change.
 * [tokens] are the words as shown on the tap chips (punctuation dropped), [words] the same words as Vosk spells them.
 */
@Serializable
data class Sentence(val id: String, val text: String) {
  val tokens: List<String>
    get() = text.split(' ').map { it.trim(',', '.', '!', '?', ';', ':', '"') }.filter { it.isNotEmpty() }

  val words: List<String>
    get() = tokens.map(::normalize)

  companion object {
    /** Lower case, curly apostrophes made straight: Vosk's en-us vocabulary spells "let's", "i'm", "don't". */
    fun normalize(word: String): String = word.lowercase().replace('’', '\'')
  }
}

/** The pool in assets/repeat/sentences.json (frozen 2026-09-29; the user approved all 30). */
object RepeatSentences {
  const val ASSET = "repeat/sentences.json"

  @Serializable private data class Pool(val sentences: List<Sentence>)

  private val json = Json { ignoreUnknownKeys = true }

  fun parse(text: String): List<Sentence> = json.decodeFromString<Pool>(text).sentences
}

/** A word Vosk recognized and its confidence (0..1). */
data class HeardWord(val word: String, val conf: Float)

/**
 * What one listening turn heard. Empty [words] means nothing was said (or only sounds outside the grammar).
 *
 * @property lagMs from the end of the last word to the result, when known (the wait the user feels).
 * @property peakDb the loudest 100 ms of the turn in dBFS, for telling a quiet voice from a miss.
 */
data class Heard(val words: List<HeardWord>, val lagMs: Long? = null, val peakDb: Float? = null) {
  companion object {
    val NOTHING = Heard(emptyList())
  }
}

/**
 * How one try is judged. These are the numbers the dev recordings tune and that are then frozen before the held-out
 * ones (docs/spikes/3.5-repeat-after-rin.md); [RepeatMatcher] applies them.
 *
 * @property minConf a heard word counts only at or above this confidence.
 * @property minCoverage the share of the sentence's words that must be heard, in order.
 * @property decoys whether the grammar carries [RepeatMatcher.DECOYS], so speech that is not the sentence has
 *   somewhere to go other than the sentence's own words (S3 had three replies to choose from; here there is one).
 */
data class MatchRules(val minConf: Float = 0.6f, val minCoverage: Float = 0.75f, val decoys: Boolean = true)

/** The result of matching one try: [matched] of the sentence's [of] words were heard in order. */
data class Match(val matched: Int, val of: Int, val needed: Int) {
  val accepted: Boolean
    get() = of > 0 && matched >= needed
}

object RepeatMatcher {
  /**
   * Common spoken words and fillers, none of them unusual: with only the sentence's words in the grammar, Vosk would
   * force any sound into them. Frozen with the rules; each must be in the model's vocabulary (the lab logs misses).
   */
  val DECOYS: List<String> =
    listOf(
      "yes", "no", "okay", "what", "where", "when", "why", "how", "who", "this", "that", "it", "is", "are", "was",
      "were", "be", "do", "does", "did", "go", "get", "got", "come", "can", "could", "would", "should", "will", "want",
      "need", "know", "think", "like", "just", "really", "very", "not", "oh", "um", "uh", "hmm", "hello", "hi", "hey",
      "right", "yeah", "well", "so", "but", "or", "if", "there", "here", "they", "we", "you", "he", "she", "them",
      "me", "my", "one", "two", "three", "more", "some", "all", "out", "in", "on", "at", "off", "over", "after",
      "before", "still", "only", "about", "with", "from", "into", "sleep", "five", "minutes",
    )

  /** Vosk's grammar for one sentence: its words, the decoys, and [unk] for anything else. */
  fun grammar(sentence: Sentence, rules: MatchRules): List<String> =
    (sentence.words + (if (rules.decoys) DECOYS else emptyList())).distinct() + "[unk]"

  fun needed(words: Int, rules: MatchRules): Int = ceil(rules.minCoverage * words - 1e-6).toInt().coerceIn(1, maxOf(words, 1))

  /** The longest in-order run of the sentence's words among the confident heard words (LCS). */
  fun match(sentence: Sentence, heard: Heard, rules: MatchRules): Match {
    val want = sentence.words
    val got = heard.words.filter { it.conf >= rules.minConf }.map { Sentence.normalize(it.word) }
    val lcs = Array(want.size + 1) { IntArray(got.size + 1) }
    for (i in want.indices) {
      for (j in got.indices) {
        lcs[i + 1][j + 1] = if (want[i] == got[j]) lcs[i][j] + 1 else maxOf(lcs[i][j + 1], lcs[i + 1][j])
      }
    }
    return Match(lcs[want.size][got.size], want.size, needed(want.size, rules))
  }
}

/**
 * The shape of one game (plan phase-3 §3): [sentences] to repeat, [tries] spoken tries each before the tap fallback,
 * and how long the mic listens per try. [feedbackMs]/[missMs] are how long "Nice!" and "Once more" stay up.
 */
data class RepeatRules(
  val sentences: Int = 3,
  val tries: Int = 2,
  val listenMs: Long = 8_000,
  val feedbackMs: Long = 900,
  val missMs: Long = 1_200,
  val match: MatchRules = MatchRules(),
) {
  init {
    require(sentences >= 1 && tries >= 1)
  }

  /** Subtitles only, no clip (release builds until Phase 4): time to read the line before the mic opens. */
  fun readingMs(sentence: Sentence): Long = 600 + 55L * sentence.text.length
}

enum class RepeatPhase {
  /** Waiting for "Let's play". */
  READY,
  /** Rin says the sentence (or, with no clip, the user reads it). */
  SPEAKING,
  /** The mic is open for one try. */
  LISTENING,
  /** "Nice!" or "Once more" for a moment. */
  FEEDBACK,
  /** Out of spoken tries (or no mic): tap the words in order. */
  TAPPING,
  PASSED,
}

enum class Feedback {
  RIGHT,
  /** Words were heard, but not enough of the sentence. */
  MISSED,
  /** Nothing was heard. */
  NOTHING,
}

/** One word chip in the tap fallback; [used] once it has been placed. */
data class Chip(val text: String, val used: Boolean = false)

/**
 * Everything the ring screen draws for the game.
 *
 * @property index 0-based sentence number; [sentence] the one on screen.
 * @property tries spoken tries used on this sentence.
 * @property chips the tap fallback's shuffled words, [placed] how many of the sentence are in place, [wrongTaps]
 *   bumps on each wrong chip (the screen shakes it).
 * @property voiceOff the mic could not be used this ring: every sentence goes straight to the chips.
 */
data class RepeatState(
  val phase: RepeatPhase = RepeatPhase.READY,
  val index: Int = 0,
  val count: Int = 3,
  val sentence: Sentence? = null,
  val tries: Int = 0,
  val maxTries: Int = 2,
  val feedback: Feedback? = null,
  val chips: List<Chip> = emptyList(),
  val placed: Int = 0,
  val wrongTaps: Int = 0,
  val voiceOff: Boolean = false,
  val voicePasses: Int = 0,
  val tapPasses: Int = 0,
  val hearAgains: Int = 0,
  /** RepeatRules.listenMs, for the screen's listening bar. */
  val listenMs: Long = 8_000,
)

/**
 * Repeat after Rin (D17, plan phase-3 §3) as a pure state machine; RepeatAfterRinMission performs what each phase
 * asks for (Rin's voice, the mic, the feedback pause) and feeds the results back. Three sentences from the pool, no
 * repeats in a game. Each gets [RepeatRules.tries] spoken tries, then the chips: the game can always be finished by
 * tapping, so an accent or a mumble never traps anyone.
 */
class RepeatGame(private val rules: RepeatRules, pool: List<Sentence>, private val random: Random) {
  private val picked: List<Sentence>

  init {
    require(pool.size >= rules.sentences) { "pool has ${pool.size} sentences, a game needs ${rules.sentences}" }
    picked = pool.shuffled(random).take(rules.sentences)
  }

  var state = RepeatState(count = rules.sentences, maxTries = rules.tries, listenMs = rules.listenMs)
    private set

  private val trace = mutableListOf<String>()

  /** Every try, for the log: `R07.1:5/6ok` (sentence, try, words heard in order / words), `R07.t` a tap pass. */
  fun trace(): String = trace.joinToString(",")

  val sentences: List<Sentence>
    get() = picked

  fun start(): RepeatState = if (state.phase != RepeatPhase.READY) state else set(speak(0))

  /** Rin finished the line (or the reading time passed). */
  fun spoken(): RepeatState {
    val s = state
    if (s.phase != RepeatPhase.SPEAKING) return s
    return set(if (s.voiceOff) tapping(s) else s.copy(phase = RepeatPhase.LISTENING, tries = s.tries + 1, feedback = null))
  }

  fun heard(heard: Heard): RepeatState {
    val s = state
    if (s.phase != RepeatPhase.LISTENING) return s
    val sentence = s.sentence ?: return s
    val match = RepeatMatcher.match(sentence, heard, rules.match)
    trace += "${sentence.id}.${s.tries}:${match.matched}/${match.of}" + if (match.accepted) "ok" else ""
    val feedback =
      when {
        match.accepted -> Feedback.RIGHT
        heard.words.isEmpty() -> Feedback.NOTHING
        else -> Feedback.MISSED
      }
    return set(
      s.copy(
        phase = RepeatPhase.FEEDBACK,
        feedback = feedback,
        voicePasses = s.voicePasses + if (match.accepted) 1 else 0,
      )
    )
  }

  /** The feedback pause is over. */
  fun advance(): RepeatState {
    val s = state
    if (s.phase != RepeatPhase.FEEDBACK) return s
    return set(
      when {
        s.feedback == Feedback.RIGHT ->
          if (s.index + 1 >= s.count) s.copy(phase = RepeatPhase.PASSED) else speak(s.index + 1)
        s.tries < s.maxTries -> s.copy(phase = RepeatPhase.SPEAKING)
        else -> tapping(s)
      }
    )
  }

  /**
   * "Hear again" while the mic is open: the try is abandoned and does not count, and Rin says it again. In the tap
   * fallback the screen replays her line without a phase change, so only the count moves here.
   */
  fun hearAgain(): RepeatState {
    val s = state
    return when (s.phase) {
      RepeatPhase.LISTENING -> set(s.copy(phase = RepeatPhase.SPEAKING, tries = s.tries - 1, hearAgains = s.hearAgains + 1))
      RepeatPhase.TAPPING -> set(s.copy(hearAgains = s.hearAgains + 1))
      else -> s
    }
  }

  /** A chip was tapped: right if its word is the next one of the sentence. */
  fun tap(chip: Int): RepeatState {
    val s = state
    if (s.phase != RepeatPhase.TAPPING) return s
    val sentence = s.sentence ?: return s
    val c = s.chips.getOrNull(chip) ?: return s
    if (c.used) return s
    if (Sentence.normalize(c.text) != sentence.words[s.placed]) return set(s.copy(wrongTaps = s.wrongTaps + 1))
    val chips = s.chips.toMutableList().also { it[chip] = c.copy(used = true) }
    val placed = s.placed + 1
    if (placed < sentence.words.size) return set(s.copy(chips = chips, placed = placed))
    trace += "${sentence.id}.t"
    return set(s.copy(phase = RepeatPhase.FEEDBACK, feedback = Feedback.RIGHT, chips = chips, placed = placed, tapPasses = s.tapPasses + 1))
  }

  /** The mic is gone for this ring (no permission, busy, model failed): chips from here on. */
  fun micFailed(): RepeatState {
    val s = state
    if (s.voiceOff) return s
    val off = s.copy(voiceOff = true)
    return set(if (s.phase == RepeatPhase.LISTENING) tapping(off) else off)
  }

  private fun speak(index: Int) =
    state.copy(
      phase = RepeatPhase.SPEAKING,
      index = index,
      sentence = picked[index],
      tries = 0,
      feedback = null,
      chips = emptyList(),
      placed = 0,
    )

  /** The sentence's words shuffled, never in the right order (unless there is only one way to lay them out). */
  private fun tapping(s: RepeatState): RepeatState {
    val tokens = s.sentence?.tokens.orEmpty()
    var order = tokens.indices.shuffled(random)
    if (tokens.map(Sentence::normalize).distinct().size > 1) {
      while (order.map { Sentence.normalize(tokens[it]) } == tokens.map(Sentence::normalize)) order = tokens.indices.shuffled(random)
    }
    return s.copy(phase = RepeatPhase.TAPPING, feedback = null, chips = order.map { Chip(tokens[it]) }, placed = 0)
  }

  private fun set(next: RepeatState): RepeatState {
    state = next
    return next
  }
}

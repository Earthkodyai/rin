package io.github.earthkodyai.rinalarm.mission

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatGameTest {
  private val pool = RepeatSentences.parse(File("src/main/assets/repeat/sentences.json").readText())
  private val rules = RepeatRules()

  private fun said(vararg words: String, conf: Float = 1f) = Heard(words.map { HeardWord(it, conf) })

  private fun saidAll(sentence: Sentence) = Heard(sentence.words.map { HeardWord(it, 1f) })

  @Test
  fun thePool_isThe30ApprovedSentences_withUniqueIds_andEnoughWordsEach() {
    assertEquals(30, pool.size)
    assertEquals(pool.size, pool.map { it.id }.toSet().size)
    assertEquals("R01", pool.first().id)
    assertEquals(listOf("let's", "make", "a", "quick", "breakfast"), pool.single { it.id == "R06" }.words)
    // Short enough to say half asleep, long enough that a stray word cannot pass (see the matcher's coverage).
    pool.forEach { assertTrue(it.text, it.words.size in 4..8) }
  }

  @Test
  fun matcher_needsMostWords_inOrder_andConfident() {
    val s = Sentence("X", "I will drink a glass of water.")
    val m = MatchRules(minConf = 0.6f, minCoverage = 0.75f)
    assertEquals(6, RepeatMatcher.needed(7, m))
    assertTrue(RepeatMatcher.match(s, saidAll(s), m).accepted)
    // One word dropped is fine; the same words out of order are not.
    assertTrue(RepeatMatcher.match(s, said("i", "will", "drink", "glass", "of", "water"), m).accepted)
    assertFalse(RepeatMatcher.match(s, said("water", "of", "glass", "a", "drink", "will", "i"), m).accepted)
    // Low-confidence words do not count.
    assertFalse(RepeatMatcher.match(s, Heard(s.words.map { HeardWord(it, 0.3f) }), m).accepted)
    // Decoys and unknown words around it change nothing.
    assertTrue(RepeatMatcher.match(s, said("um", "i", "will", "drink", "a", "glass", "of", "water", "okay"), m).accepted)
    assertFalse(RepeatMatcher.match(s, Heard.NOTHING, m).accepted)
  }

  @Test
  fun grammar_isTheSentencesWords_plusDecoys_plusUnk() {
    val s = Sentence("X", "Let's check the weather outside.")
    val g = RepeatMatcher.grammar(s, MatchRules(decoys = true))
    assertEquals(listOf("let's", "check", "the", "weather", "outside"), g.take(5))
    assertEquals("[unk]", g.last())
    assertEquals(g.size, g.toSet().size)
    // Both forms of a contraction, so "let us check" is heard too.
    assertEquals(
      listOf("let's", "check", "the", "weather", "outside", "let", "us", "[unk]"),
      RepeatMatcher.grammar(s, MatchRules(decoys = false)),
    )
    assertEquals(
      listOf("i", "am", "ready", "i'm", "[unk]"),
      RepeatMatcher.grammar(Sentence("Y", "I am ready."), MatchRules(decoys = false)),
    )
  }

  @Test
  fun contractions_matchTheirLongForms_bothWays() {
    val m = MatchRules(minConf = 0.6f, minCoverage = 1f)
    assertTrue(RepeatMatcher.match(Sentence("A", "I am ready to start the day."), said("i'm", "ready", "to", "start", "the", "day"), m).accepted)
    assertTrue(RepeatMatcher.match(Sentence("B", "I'm getting out of bed now."), said("i", "am", "getting", "out", "of", "bed", "now"), m).accepted)
  }

  @Test
  fun noDecoy_soundsLikeAPoolWord() {
    val poolWords = pool.flatMap { RepeatMatcher.expand(it.words) }.toSet()
    val clashes =
      poolWords.flatMap { w -> RepeatMatcher.HOMOPHONES.filter { w in it }.flatMap { it - w } }.filter { it in RepeatMatcher.DECOYS }
    assertEquals(emptyList<String>(), clashes)
  }

  @Test
  fun aGameByVoice_threeDifferentSentences_eachHeardOnce() {
    val game = RepeatGame(rules, pool, Random(1))
    assertEquals(3, game.sentences.toSet().size)
    game.start()
    repeat(3) { i ->
      assertEquals(RepeatPhase.SPEAKING, game.state.phase)
      assertEquals(i, game.state.index)
      game.spoken()
      assertEquals(RepeatPhase.LISTENING, game.state.phase)
      assertEquals(1, game.state.tries)
      game.heard(saidAll(game.state.sentence!!))
      assertEquals(Feedback.RIGHT, game.state.feedback)
      game.advance()
    }
    assertEquals(RepeatPhase.PASSED, game.state.phase)
    assertEquals(3, game.state.voicePasses)
    assertEquals(0, game.state.tapPasses)
    assertTrue(game.trace(), game.trace().split(",").all { it.endsWith("ok") })
  }

  @Test
  fun twoMissedTries_thenTheChips_whichAlwaysFinishTheSentence() {
    val game = RepeatGame(rules, pool, Random(2))
    game.start()
    game.spoken()
    game.heard(Heard.NOTHING)
    assertEquals(Feedback.NOTHING, game.state.feedback)
    game.advance()
    assertEquals(RepeatPhase.SPEAKING, game.state.phase) // Rin says it again
    game.spoken()
    assertEquals(2, game.state.tries)
    game.heard(said("hmm", "okay"))
    assertEquals(Feedback.MISSED, game.state.feedback)
    game.advance()
    assertEquals(RepeatPhase.TAPPING, game.state.phase)

    val sentence = game.state.sentence!!
    assertNotEquals(sentence.tokens, game.state.chips.map { it.text }) // never already in order
    assertEquals(sentence.tokens.sorted(), game.state.chips.map { it.text }.sorted())
    // A wrong chip is counted and changes nothing else.
    val wrong = game.state.chips.indexOfFirst { Sentence.normalize(it.text) != sentence.words[0] }
    game.tap(wrong)
    assertEquals(1, game.state.wrongTaps)
    assertEquals(0, game.state.placed)
    sentence.words.forEach { word ->
      game.tap(game.state.chips.indexOfFirst { !it.used && Sentence.normalize(it.text) == word })
    }
    assertEquals(RepeatPhase.FEEDBACK, game.state.phase)
    assertEquals(Feedback.RIGHT, game.state.feedback)
    assertEquals(1, game.state.tapPasses)
    assertTrue(game.trace(), game.trace().endsWith("${sentence.id}.t"))
    game.advance()
    assertEquals(1, game.state.index)
  }

  @Test
  fun hearAgain_whileListening_doesNotCostATry() {
    val game = RepeatGame(rules, pool, Random(3))
    game.start()
    game.spoken()
    game.hearAgain()
    assertEquals(RepeatPhase.SPEAKING, game.state.phase)
    assertEquals(0, game.state.tries)
    assertEquals(1, game.state.hearAgains)
    game.spoken()
    assertEquals(1, game.state.tries)
  }

  @Test
  fun aBrokenMic_sendsEverySentenceToTheChips() {
    val game = RepeatGame(rules, pool, Random(4))
    game.start()
    game.spoken()
    game.micFailed()
    assertEquals(RepeatPhase.TAPPING, game.state.phase)
    game.state.sentence!!.words.forEach { w -> game.tap(game.state.chips.indexOfFirst { !it.used && Sentence.normalize(it.text) == w }) }
    game.advance()
    assertEquals(RepeatPhase.SPEAKING, game.state.phase)
    game.spoken() // she still says it; then straight to the chips
    assertEquals(RepeatPhase.TAPPING, game.state.phase)
  }

  @Test
  fun theSameSeed_picksTheSameSentences() {
    assertEquals(RepeatGame(rules, pool, Random(9)).sentences, RepeatGame(rules, pool, Random(9)).sentences)
  }

  @Test
  fun repeatedWords_canBeTappedFromEitherChip() {
    val s = Sentence("X", "one two one three")
    val game = RepeatGame(RepeatRules(sentences = 1, tries = 1), listOf(s), Random(5))
    game.start()
    game.spoken()
    game.heard(Heard.NOTHING)
    game.advance()
    val chips = game.state.chips
    // Tap the second "one" chip for the first word, then the first one for the third word.
    val ones = chips.indices.filter { chips[it].text == "one" }
    game.tap(ones[1])
    game.tap(chips.indexOfFirst { it.text == "two" })
    game.tap(ones[0])
    game.tap(chips.indexOfFirst { it.text == "three" })
    assertEquals(Feedback.RIGHT, game.state.feedback)
  }
}

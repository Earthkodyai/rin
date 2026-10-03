package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.mission.TournamentGame
import io.github.earthkodyai.rinalarm.mission.TournamentLadder
import io.github.earthkodyai.rinalarm.mission.TournamentScore
import io.github.earthkodyai.rinalarm.tournament.TournamentViewModel
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's limits and firebase/firestore.rules say the same thing, and no real run falls foul of them. */
class OnlineLimitsTest {
  private val rules = File("../firebase/firestore.rules").readText()

  @Test
  fun theRulesUseTheSameNumbers() {
    for (expected in
      listOf(
        "d.levels <= ${OnlineLimits.MAX_LEVELS}",
        "d.timeMs >= d.levels * ${OnlineLimits.MIN_MS_PER_LEVEL}",
        "d.timeMs <= ${OnlineLimits.MAX_TIME_MS}",
        "duration.value(${OnlineLimits.UPDATE_GAP_S}, 's')",
        "u.matches('${OnlineLimits.UNIVERSITY_ID.pattern}')",
        "n.size() <= 20",
        "['pads', 'cups']",
      )) {
      assertTrue("firestore.rules lacks: $expected", expected in rules)
    }
    assertTrue(TournamentGame.entries.map { it.stored } == listOf("pads", "cups"))
  }

  @Test
  fun rinsDemoAloneTakesLongerThanTheRulesFloor_atEveryLevel() {
    // The time runs from GO to the last level passed: every level's demo, and the hold + LEVEL banner between levels.
    // The player's own taps come on top, so this is a lower bound on any real run.
    val between = TournamentViewModel.PASS_HOLD_MS + TournamentViewModel.BANNER_MS
    for (game in TournamentGame.entries) {
      var least = 0L
      for (level in 1..OnlineLimits.MAX_LEVELS) {
        least +=
          when (game) {
            TournamentGame.PADS -> TournamentLadder.pads(level).let { it.lengths.first() * (it.moveMs + it.pressMs) }
            TournamentGame.CUPS -> TournamentLadder.cups(level).let { it.swaps.first() * (it.swapMs + it.gapMs) }
          }
        if (level > 1) least += between
        assertTrue("$game level $level: ${least}ms", least >= level * OnlineLimits.MIN_MS_PER_LEVEL)
      }
    }
  }

  @Test
  fun postable_followsTheRules() {
    assertTrue(OnlineLimits.postable(TournamentScore(TournamentGame.PADS, 1, 1_000)))
    assertTrue(OnlineLimits.postable(TournamentScore(TournamentGame.PADS, 500, 500_000)))
    assertFalse(OnlineLimits.postable(TournamentScore(TournamentGame.PADS, 0, 0)))
    assertFalse(OnlineLimits.postable(TournamentScore(TournamentGame.PADS, 5, 4_999)))
    assertFalse(OnlineLimits.postable(TournamentScore(TournamentGame.PADS, 501, 600_000)))
    assertFalse(OnlineLimits.postable(TournamentScore(TournamentGame.CUPS, 5, OnlineLimits.MAX_TIME_MS + 1)))
  }
}

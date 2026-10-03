package io.github.earthkodyai.rinalarm.ui.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tournament start page's walkthrough (6.10): one card at a time, top to bottom, and each card its own light. */
class StartGuideTest {
  @Test
  fun stepsGoDownThePage_andEndAfterTheRules() {
    val walk = generateSequence(StartGuideStep.entries.first()) { it.next }.toList()
    assertEquals(listOf(StartGuideStep.GAME, StartGuideStep.YOU, StartGuideStep.ONLINE, StartGuideStep.RULES), walk)
    assertNull(StartGuideStep.RULES.next)
  }

  @Test
  fun everyStepLightsADifferentCard_neverTheBanner() {
    val spots = StartGuideStep.entries.map { it.spot }
    assertEquals(spots.size, spots.toSet().size)
    assert(StartSpot.BANNER !in spots)
  }
}

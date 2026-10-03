package io.github.earthkodyai.rinalarm.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tournament's university list (G.7): the Webometrics July 2026 top 50 in Thailand, one entry each. */
class UniversitiesTest {
  private val all = Universities.all

  @Test
  fun fiftyUniversities_rankedOneToFifty() {
    assertEquals(50, all.size)
    assertEquals((1..50).toList(), all.map { it.rank })
  }

  @Test
  fun idsAndShortNamesAreUnique_andShortEnoughForTheRulesAndABadge() {
    assertEquals(all.size, all.map { it.id }.toSet().size)
    assertEquals(all.size, all.map { it.short }.toSet().size)
    for (u in all) {
      assertTrue(u.id, u.id.matches(Regex("[a-z]{2,8}")))
      assertEquals(u.id.uppercase(), u.short)
    }
  }

  @Test
  fun everyEntryHasAnEnglishAndAThaiName() {
    val thai = Regex("[฀-๿]")
    for (u in all) {
      assertTrue(u.id, u.english.isNotBlank() && !thai.containsMatchIn(u.english))
      assertTrue(u.id, thai.containsMatchIn(u.thai))
    }
    assertEquals(all.size, all.map { it.english }.toSet().size)
  }

  @Test
  fun thePickerIsAToZ_withEveryUniversity() {
    val names = Universities.alphabetical.map { it.english.lowercase() }
    assertEquals(names.sorted(), names)
    assertEquals(all.toSet(), Universities.alphabetical.toSet())
  }

  @Test
  fun lookup_byIdOnly() {
    assertEquals("Kasetsart University", Universities["ku"]?.english)
    assertNull(Universities[null])
    assertNull(Universities["KU"])
    assertEquals("unis/ku.png", Universities.logoAsset("ku"))
  }
}

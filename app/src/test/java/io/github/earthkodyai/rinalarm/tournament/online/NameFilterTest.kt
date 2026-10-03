package io.github.earthkodyai.rinalarm.tournament.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameFilterTest {
  @Test
  fun everydayNames_pass() {
    for (name in
      listOf("", "Earth", "Mint KU", "เอิร์ธ", "พลอย ม.เกษตร", "Pornchai", "Porntip", "Classic Bass", "Cocktail", "Dickens",
        "Scuba Steve", "Niger", "Fukuoka", "Assam tea", "หีบเพลง", "แม่งาน", "สัดส่วน", "แกงกะหรี่", "Rin fan 🌸", "x7_99")) {
      assertTrue("refused: $name", NameFilter.allowed(name))
    }
  }

  @Test
  fun insultsAndSlurs_fail_inEnglishAndThai() {
    for (name in listOf("fuck", "Shit head", "bitch", "nigger", "asshole", "ควย", "เหี้ย", "ไอ้สัตว์", "อีดอก", "แม่ง")) {
      assertFalse("allowed: $name", NameFilter.allowed(name))
    }
  }

  @Test
  fun disguises_fail_spacingPunctuationStretchingAndDigits() {
    for (name in listOf("f u c k", "f.u.c.k", "FuUuCk", "sh1t", "5h1t", "b!tch", "a$\$hole", "ค ว ย", "เ-หี้-ย")) {
      assertFalse("allowed: $name", NameFilter.allowed(name))
    }
  }

  @Test
  fun shortWords_failOnlyOnTheirOwn() {
    assertFalse(NameFilter.allowed("ass"))
    assertFalse(NameFilter.allowed("big ass"))
    assertFalse(NameFilter.allowed("Sex"))
    assertTrue(NameFilter.allowed("class"))
    assertTrue(NameFilter.allowed("Essex"))
  }

  @Test
  fun typed_dropsHiddenCharactersAndKeepsTwenty() {
    assertEquals("Earth", NameFilter.typed("Ea​rth"))
    assertEquals("htrae", NameFilter.typed("‮htrae"))
    assertEquals("x".repeat(20), NameFilter.typed("x".repeat(25)))
    assertFalse(NameFilter.allowed("Ea​rth"))
  }
}

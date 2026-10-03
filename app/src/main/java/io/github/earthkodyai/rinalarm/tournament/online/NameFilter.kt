package io.github.earthkodyai.rinalarm.tournament.online

import io.github.earthkodyai.rinalarm.data.TournamentEntry
import java.text.Normalizer

/**
 * Public names are user-generated content (Play policy, ADR 0008): this keeps the obvious insults and slurs, in Thai and
 * English, off the board before a name is posted. It cannot catch everything, so every row also has Report, and the
 * developer deletes rows and bans players in the Firebase console.
 *
 * Matching ignores case, spacing, punctuation, stretched letters and the usual digit swaps (sh1t, f.u.c.k, fuuuck); a
 * listed double letter stays double, so "Niger" is not "nigger". Short words that hide inside clean ones (ass in class,
 * cum in document) match only as whole words, and common Thai names and words that contain a listed one (Porn-, แม่งาน,
 * สัดส่วน) are left out or excused.
 */
object NameFilter {
  /** Characters the rules refuse because they can disguise a name: control, zero-width and direction overrides. */
  private val hidden = Regex("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]")

  /** Found anywhere in the name, with everything but letters and Thai marks taken out. */
  private val anywhere =
    listOf(
      // English
      "fuck", "fck", "shit", "bitch", "cunt", "nigger", "nigga", "faggot", "fagot", "whore", "slut", "rapist",
      "asshole", "motherf", "dickhead", "pussy", "retard", "bastard", "hitler", "nazi", "penis", "vagina",
      "dildo", "blowjob", "cocksuck", "jerkoff", "wanker", "twat",
      // Thai
      "ควย", "เหี้ย", "เย็ด", "สัส", "สัด", "แตด", "ระยำ", "ชาติหมา", "จัญไร", "ส้นตีน", "ตีนตบ", "ดอกทอง",
      "อีดอก", "ไอ้สัตว์", "อีสัตว์", "เงี่ยน", "ขายตัว", "อีตัว", "แม่ง", "พ่อมึง", "แม่มึง", "มึงตาย", "กะหรี่", "ร่าน",
      "หน้าหี", "หีแม่", "หีมึง",
    )

  /** Whole words only, after splitting on anything that is not a letter or digit. */
  private val words = setOf("ass", "cum", "fag", "dick", "cock", "sex", "rape", "tits", "hoe", "kys", "หี")

  /** Thai words that contain a listed one but are clean. */
  private val cleanThai = listOf("หีบ", "สัสดี", "สัดส่วน", "แม่งาน", "กะหรี่ปั๊บ", "แกงกะหรี่", "ผงกะหรี่")

  private val leet = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i')

  /** What the text box keeps of what was typed: no hidden characters, at most [TournamentEntry.NAME_MAX]. */
  fun typed(text: String): String = text.replace(hidden, "").take(TournamentEntry.NAME_MAX)

  /** True when [name] (already trimmed) may go on the board. "" is allowed: the board shows "Player" and an id. */
  fun allowed(name: String): Boolean {
    if (name.isEmpty()) return true
    if (hidden.containsMatchIn(name) || name.length > TournamentEntry.NAME_MAX) return false
    var text = Normalizer.normalize(name, Normalizer.Form.NFKC).lowercase()
    cleanThai.forEach { text = text.replace(it, " ") }
    val swapped = text.map { leet[it] ?: it }.joinToString("")
    if (anywhereRegex.containsMatchIn(swapped.filter { it.isLetterOrDigit() || it.isThaiMark() })) return false
    return swapped.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).none { wordRegex.matches(it) }
  }

  /** Each letter one or more times: f+u+c+k+ catches "fuuuck", and n+i+g+g+e+r+ still needs the double g. */
  private fun stretchy(word: String): String = word.map { Regex.escape(it.toString()) + "+" }.joinToString("")

  private val anywhereRegex = Regex(anywhere.joinToString("|", transform = ::stretchy))

  private val wordRegex = Regex(words.joinToString("|", transform = ::stretchy))

  /** Thai vowel and tone marks are not letters to Kotlin, but they are part of the word. */
  private fun Char.isThaiMark(): Boolean = this in 'ั'..'๎'
}

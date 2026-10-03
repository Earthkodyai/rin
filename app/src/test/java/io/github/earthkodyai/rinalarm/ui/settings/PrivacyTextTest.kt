package io.github.earthkodyai.rinalarm.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy notice (6.3): the app shows PRIVACY.md itself, and what it promises must stay true of the build. */
class PrivacyTextTest {
  private val english = File("../PRIVACY.md").readText()
  private val thai = File("../PRIVACY.th.md").readText()
  private val manifest = File("src/main/AndroidManifest.xml").readText()

  @Test
  fun theNotice_parsesIntoATitleHeadingsParagraphsAndBullets() {
    val blocks = PrivacyText.parse(english)
    assertEquals(PrivacyBlock.Title("RinAlarm privacy notice"), blocks.first())
    assertTrue(blocks.count { it is PrivacyBlock.Heading } >= 7)
    assertTrue(blocks.any { it is PrivacyBlock.Bullet })
  }

  @Test
  fun theNotice_usesNoMarkdownTheScreenWouldShowRaw() {
    val text = PrivacyText.parse(english).joinToString("\n") {
      when (it) {
        is PrivacyBlock.Title -> it.text
        is PrivacyBlock.Heading -> it.text
        is PrivacyBlock.Paragraph -> it.text
        is PrivacyBlock.Bullet -> it.text
      }
    }
    for (raw in listOf("**", "](", "`", "#", "* ")) assertFalse("raw \"$raw\" in the notice", raw in text)
  }

  @Test
  fun theThaiNotice_hasTheSameSections() {
    fun headings(markdown: String) = PrivacyText.parse(markdown).count { it is PrivacyBlock.Heading }
    assertEquals(headings(english), headings(thai))
  }

  @Test
  fun theLeaderboardIsTheOnlyNetworkUse_asTheNoticeSays() {
    assertTrue("Its only network use is the optional tournament leaderboard" in english)
    // G.6 (ADR 0008): only tournament/online (in any source set) may reach the network; no other code imports
    // Firebase or a network API.
    val network = Regex("""^import (com\.google\.firebase|java\.net\.|javax\.net\.|okhttp3|android\.net\.http)""", RegexOption.MULTILINE)
    val outside =
      File("src").walkTopDown()
        .filter { it.isFile && it.extension == "kt" && "/rinalarm/tournament/online/" !in it.invariantSeparatorsPath }
        .filter { network.containsMatchIn(it.readText()) }
        .map { it.path }
        .toList()
    assertEquals(emptyList<String>(), outside)
  }

  @Test
  fun firebaseNeverStartsWithTheApp_soTheRingPathNeverWaitsForIt() {
    val provider =
      Regex("""android:name="com.google.firebase.provider.FirebaseInitProvider"\s+android:authorities="\$\{applicationId}.firebaseinitprovider"\s+tools:node="remove"""")
    assertTrue(provider.containsMatchIn(manifest))
  }

  @Test
  fun safeBrowsingStaysOff_soPlayServicesLooksNothingUpForRinsPage() {
    // 6.3: with it on, every page load sent a lookup through Google Play services, charged to the app.
    val off = Regex("""android:name="android.webkit.WebView.EnableSafeBrowsing"\s+android:value="false"""")
    assertTrue(off.containsMatchIn(manifest))
  }

  @Test
  fun backupOff_holds() {
    assertTrue("backup is turned off" in english)
    assertTrue("""android:allowBackup="false"""" in manifest)
  }
}

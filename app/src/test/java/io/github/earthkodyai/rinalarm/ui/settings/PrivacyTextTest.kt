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
  fun noInternet_holds_theManifestRemovesThePermissionLibrariesAddIt() {
    assertTrue("no internet permission" in english)
    // ML Kit merges INTERNET in (6.2); the manifest must keep removing it.
    val internet = Regex("""<uses-permission\s+android:name="android.permission.INTERNET"\s+tools:node="remove"""")
    assertTrue(internet.containsMatchIn(manifest))
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

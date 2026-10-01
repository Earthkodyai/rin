package io.github.earthkodyai.rinalarm.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text-on-fill pair the screens use, in both looks, at WCAG's 4.5:1 for body text. */
class RinPaletteContrastTest {
  /** WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05). */
  private fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
  }

  private fun pairs(p: RinPalette) =
    mapOf(
      "ink on ground" to (p.ink to p.ground),
      "ink on card" to (p.ink to p.card),
      "ink on Rin's panel" to (p.ink to p.rinCard),
      "ink on bubble" to (p.ink to p.bubble),
      "muted on ground" to (p.muted to p.ground),
      "muted on card" to (p.muted to p.card),
      "button text" to (p.onPrimary to p.primary),
      "primary as text on card" to (p.primary to p.card),
      "primary as text on ground" to (p.primary to p.ground),
      "mint text on card" to (p.mintText to p.card),
      "tag text" to (p.onTag to p.tag),
      "sick day on" to (p.onSick to p.sickFill),
      "setup banner" to (p.onAlert to p.alert),
      "emergency stop" to (p.onStop to p.stop),
      "emergency stop, held" to (p.onStop to p.stopHeld),
      "ink on the pattern" to (p.ink to p.pattern.base),
      "ink on the pattern's stripes" to (p.ink to p.pattern.stripe),
      "secondary text on the pattern" to (p.pattern.muted to p.pattern.base),
      "secondary text on the pattern's stripes" to (p.pattern.muted to p.pattern.stripe),
    )

  @Test
  fun dayAndNightText_isReadable() {
    for (palette in listOf(DayPalette, NightPalette)) {
      for ((name, pair) in pairs(palette)) {
        val ratio = contrast(pair.first, pair.second)
        assertTrue("${if (palette.night) "night" else "day"} $name: $ratio", ratio >= 4.5f)
      }
    }
  }
}

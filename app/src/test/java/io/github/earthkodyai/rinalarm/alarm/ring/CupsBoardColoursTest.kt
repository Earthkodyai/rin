package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CupsBoardColoursTest {
  /** WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05). */
  private fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
  }

  @Test
  fun theCupsStandOutFromTheTable_byBrightnessNotOnlyHue() {
    // 3:1 is WCAG's bar for shapes that matter (non-text contrast); the old wood was 1.07:1 against the cups.
    assertTrue("cup vs table ${contrast(CUP, WOOD)}", contrast(CUP, WOOD) >= 3f)
  }
}

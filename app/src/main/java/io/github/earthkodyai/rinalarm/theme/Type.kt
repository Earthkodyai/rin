package io.github.earthkodyai.rinalarm.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.earthkodyai.rinalarm.R

/**
 * M PLUS Rounded 1c, the rounded anime-style face of the mockups (UX phase), bundled subset to Latin and punctuation
 * (~47 KB a weight; OFL, assets/licenses/OFL-MPLUSRounded1c.txt).
 */
val RinRounded =
  FontFamily(
    Font(R.font.mplus_rounded_regular, FontWeight.Normal),
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
  )

private val base = Typography()

private fun TextStyle.rin(weight: FontWeight? = null) = copy(fontFamily = RinRounded, fontWeight = weight ?: fontWeight)

/** Material's type scale in Rin's font; titles and labels a step bolder for the playful look. */
val Typography =
  Typography(
    displayLarge = base.displayLarge.rin(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.rin(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.rin(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.rin(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.rin(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.rin(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.rin(FontWeight.ExtraBold),
    titleMedium = base.titleMedium.rin(FontWeight.Bold),
    titleSmall = base.titleSmall.rin(FontWeight.Bold),
    bodyLarge = base.bodyLarge.rin(),
    bodyMedium = base.bodyMedium.rin(),
    bodySmall = base.bodySmall.rin(),
    labelLarge = base.labelLarge.rin(FontWeight.Bold),
    labelMedium = base.labelMedium.rin(FontWeight.Bold),
    labelSmall = base.labelSmall.rin(FontWeight.Bold),
  )

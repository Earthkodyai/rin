package io.github.earthkodyai.rinalarm.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * RinAlarm's fixed palettes (UX phase, the user's picks, 2026-10-01): "Candy morning" by day, the starry night look
 * at night, the same shapes in both. Text on a fill is checked for contrast (WCAG, RinPaletteContrastTest): white on
 * [primary] by day is about 5:1 and the pink as text on the cream ground 4.6:1, which is why the button pink is deeper
 * than the decorative [candy] pink of the mockups (2.6:1).
 *
 * @property ground       the screen behind everything
 * @property card         alarm cards, sheets, bubbles
 * @property rinCard      the panel Rin stands in
 * @property primary      filled buttons and selected states; [onPrimary] is their text
 * @property candy        decoration only (switch tracks, day dots): never under text
 * @property mint         the second accent (sick day, progress); [mintText] when it is text on [card]
 * @property ink          body text; [muted] secondary text (4.5:1 or more on [ground] and [card])
 * @property line         outlines and quiet borders
 * @property hardShadow   the solid drop under cards and buttons that gives the playful, sticker-like look
 * @property glow         the sun by day, the moon by night, behind Rin
 * @property tag          small labels on a card (an alarm's game); [onTag] is their text
 * @property bubble       Rin's speech bubble on her panel
 * @property alert        the deep red of a warning that an alarm could fail (the setup banner); [onAlert] its text,
 *                        [alertShadow] its drop
 * @property stop         the bright red of the ring screen's emergency stop, meant to look dangerous (the user,
 *                        2026-10-02); [onStop] its text, [stopHeld] the fill that grows while it is held, [stopShadow]
 *                        its drop. The same in both looks, like [alert]
 */
@Immutable
data class RinPalette(
  val night: Boolean,
  val ground: Color,
  val card: Color,
  val rinCard: Color,
  val primary: Color,
  val onPrimary: Color,
  val primaryShadow: Color,
  val candy: Color,
  val mint: Color,
  val mintText: Color,
  val ink: Color,
  val muted: Color,
  val line: Color,
  val hardShadow: Color,
  val glow: Color,
  val danger: Color,
  val tag: Color,
  val onTag: Color,
  val bubble: Color,
  val alert: Color = Color(0xFFB3261E),
  val onAlert: Color = Color(0xFFFFFFFF),
  val alertShadow: Color = Color(0xFF7A1A14),
  val stop: Color = Color(0xFFE0202A),
  val onStop: Color = Color(0xFFFFFFFF),
  val stopHeld: Color = Color(0xFF9E0E16),
  val stopShadow: Color = Color(0xFF7E0A10),
)

val DayPalette =
  RinPalette(
    night = false,
    ground = Color(0xFFFFF4EA),
    card = Color(0xFFFFFFFF),
    rinCard = Color(0xFFFFE1E9),
    primary = Color(0xFFCC3169),
    onPrimary = Color(0xFFFFFFFF),
    primaryShadow = Color(0xFF9E2350),
    candy = Color(0xFFFF6F9C),
    mint = Color(0xFF3DBE9A),
    mintText = Color(0xFF167A5D),
    ink = Color(0xFF3A2A33),
    muted = Color(0xFF7A5A68),
    line = Color(0xFFF1D2C2),
    hardShadow = Color(0xFFF1D2C2),
    glow = Color(0xFFFFD27A),
    danger = Color(0xFF8A4A4A),
    tag = Color(0xFFFFF0D6),
    onTag = Color(0xFF8A5A00),
    bubble = Color(0xFFFFFFFF),
  )

val NightPalette =
  RinPalette(
    night = true,
    ground = Color(0xFF121836),
    card = Color(0xFF1C2350),
    rinCard = Color(0xFF1C2350),
    primary = Color(0xFFFF8FB8),
    onPrimary = Color(0xFF121836),
    primaryShadow = Color(0xFFB85C82),
    candy = Color(0xFFFF8FB8),
    mint = Color(0xFF7FE3C4),
    mintText = Color(0xFF7FE3C4),
    ink = Color(0xFFF3EEFF),
    muted = Color(0xFFB9B3D9),
    line = Color(0xFF3B4687),
    hardShadow = Color(0xFF0B1028),
    glow = Color(0xFFFFE9A8),
    danger = Color(0xFFFFB4B4),
    tag = Color(0xFF262E63),
    onTag = Color(0xFFFFE9A8),
    bubble = Color(0xFF262E63),
  )

/** The sick-day button when it is on: mint, deep enough by day for white text, bright at night under dark text. */
val RinPalette.sickFill: Color
  get() = if (night) mint else mintText

val RinPalette.onSick: Color
  get() = if (night) ground else card

/**
 * A gold trophy's colours (the tournament: its home button and the trophies on its wallpaper), the same by day and by
 * night, with a dark brown for text and lines on it (the user, 2026-10-03: "like a gold trophy", not the pale tag).
 */
object TrophyGold {
  val light = Color(0xFFFFD04A)
  val base = Color(0xFFFCB918)
  val shade = Color(0xFFEAA012)
  val deep = Color(0xFFB8720A)
  val ink = Color(0xFF4A2A06)
}

/** The ring screen's wallpaper (ui/common/RinPattern.kt), by day and by night. */
@Immutable
data class PatternColors(
  val base: Color,
  val stripe: Color,
  val heart: Color,
  val clockFill: Color,
  val clockLine: Color,
  val star: Color,
  val cup: Color,
  val dash: Color,
  /** Secondary text over it: [RinPalette.muted] was 3.8:1 on the day stripes. */
  val muted: Color,
)

private val DayPattern =
  PatternColors(
    base = Color(0xFFFFCADB),
    stripe = Color(0xFFFFBCD0),
    heart = Color(0xFFFF7FA3),
    clockFill = Color(0xFFFFFFFF),
    clockLine = Color(0xFFF59AB4),
    star = Color(0xFFFFC94D),
    cup = Color(0xFFE8697A),
    dash = Color(0xFFFFFFFF),
    muted = Color(0xFF6A4A58),
  )

private val NightPattern =
  PatternColors(
    base = Color(0xFF262E63),
    stripe = Color(0xFF2D3772),
    heart = Color(0xFFFF8FB8),
    clockFill = Color(0xFF3B4687),
    clockLine = Color(0xFF8F97D6),
    star = Color(0xFFFFE9A8),
    cup = Color(0xFFC25A72),
    dash = Color(0xFF4A56A0),
    muted = Color(0xFFB9B3D9),
  )

val RinPalette.pattern: PatternColors
  get() = if (night) NightPattern else DayPattern

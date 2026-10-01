package io.github.earthkodyai.rinalarm.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * RinAlarm's fixed palettes (UX phase, the user's picks, 2026-10-01): "Candy morning" by day, the starry night look
 * at night, the same shapes in both. Text on a fill is checked for contrast (WCAG): white on [primary] by day is about
 * 4.9:1, which is why the button pink is deeper than the decorative [candy] pink of the mockups (2.6:1).
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
)

val DayPalette =
  RinPalette(
    night = false,
    ground = Color(0xFFFFF4EA),
    card = Color(0xFFFFFFFF),
    rinCard = Color(0xFFFFE1E9),
    primary = Color(0xFFCF346D),
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
  )

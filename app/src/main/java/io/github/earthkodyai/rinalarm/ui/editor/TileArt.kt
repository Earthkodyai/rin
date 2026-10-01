package io.github.earthkodyai.rinalarm.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.theme.RinTheme

/**
 * The mark beside an editor tile's name (the user, 2026-10-02: minimal, the fewest shapes that say what the game is or
 * what the theme sounds like). One mark on a round badge of its own colour; decoration only, the name is what is read.
 */
enum class TileMark(private val day: Long, private val night: Long) {
  SHUFFLE(0xFFCC3169, 0xFFFF8FB8),
  PADS(0xFF3E7BFA, 0xFF8FB3FF),
  CUPS(0xFFD9467A, 0xFFFF9EC0),
  SPEECH(0xFF2C8FB8, 0xFF8FD3F0),
  BUTTON(0xFF7A5A68, 0xFFB9B3D9),
  SPARKLE(0xFF7C4DDB, 0xFFC4A8FF),
  CAFE(0xFF9A5B2E, 0xFFE3B48F),
  PIXEL(0xFFD63B57, 0xFFFF9AAE),
  LEAF(0xFF2E8B57, 0xFF8FE0B0),
  WAVE(0xFF8A5A00, 0xFFFFE9A8);

  fun color(night: Boolean) = Color(if (night) this.night else day)

  companion object {
    fun of(choice: MissionChoice): TileMark =
      when (choice) {
        MissionChoice.RinPicks -> SHUFFLE
        MissionChoice.None -> BUTTON
        is MissionChoice.Only ->
          when (choice.type) {
            MissionType.PADS -> PADS
            MissionType.CUPS -> CUPS
            MissionType.SPEECH -> SPEECH
          }
      }

    /** A theme by its id; one added later without a mark of its own gets the wave. */
    fun of(sound: AlarmSound): TileMark =
      when (sound) {
        AlarmSound.RinPicks -> SHUFFLE
        AlarmSound.Beep -> WAVE
        is AlarmSound.Theme ->
          when (sound.id) {
            "magic" -> SPARKLE
            "cafe" -> CAFE
            "arcade" -> PIXEL
            "nature" -> LEAF
            else -> WAVE
          }
      }
  }
}

/** The mark on its badge: a soft disc of its colour, the mark on it in full colour. */
@Composable
fun TileBadge(mark: TileMark, modifier: Modifier = Modifier) {
  val night = RinTheme.palette.night
  val color = mark.color(night)
  Box(
    modifier.size(BADGE).background(color.copy(alpha = if (night) 0.22f else 0.14f), CircleShape).clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(GLYPH)) { drawMark(mark, color) }
  }
}

private val BADGE = 32.dp
private val GLYPH = 18.dp

/** Every mark is drawn in an 18-unit square, scaled to the canvas. */
private fun DrawScope.drawMark(mark: TileMark, color: Color) {
  val u = size.minDimension / 18f
  val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
  fun p(x: Float, y: Float) = Offset(x * u, y * u)
  when (mark) {
    // Two crossing arrows: Rin chooses.
    TileMark.SHUFFLE -> {
      val path =
        Path().apply {
          moveTo(2f * u, 5f * u)
          cubicTo(8f * u, 5f * u, 10f * u, 13f * u, 16f * u, 13f * u)
          moveTo(2f * u, 13f * u)
          cubicTo(8f * u, 13f * u, 10f * u, 5f * u, 16f * u, 5f * u)
          moveTo(13.5f * u, 2.5f * u)
          lineTo(16f * u, 5f * u)
          lineTo(13.5f * u, 7.5f * u)
          moveTo(13.5f * u, 10.5f * u)
          lineTo(16f * u, 13f * u)
          lineTo(13.5f * u, 15.5f * u)
        }
      drawPath(path, color, style = line)
    }
    // The four pads, in the game's own colours.
    TileMark.PADS -> {
      val colors = listOf(Color(0xFFE5484D), Color(0xFF3E7BFA), Color(0xFFF5B700), Color(0xFF2EB67D))
      colors.forEachIndexed { i, c ->
        drawRoundRect(c, p(1.5f + (i % 2) * 8f, 1.5f + (i / 2) * 8f), Size(7f * u, 7f * u), CornerRadius(1.8f * u))
      }
    }
    // One upside-down cup and the ball.
    TileMark.CUPS -> {
      val cup =
        Path().apply {
          moveTo(5f * u, 2f * u)
          lineTo(13f * u, 2f * u)
          lineTo(15.5f * u, 13f * u)
          lineTo(2.5f * u, 13f * u)
          close()
        }
      drawPath(cup, color)
      drawCircle(color, 2f * u, p(9f, 16f))
    }
    // A speech bubble with an A in it.
    TileMark.SPEECH -> {
      val bubble =
        Path().apply {
          addRoundRect(RoundRect(1.5f * u, 1.5f * u, 16.5f * u, 12.5f * u, CornerRadius(3.5f * u)))
          moveTo(5f * u, 12.5f * u)
          lineTo(4f * u, 16.5f * u)
          lineTo(8.5f * u, 12.5f * u)
        }
      drawPath(bubble, color, style = line)
      val a =
        Path().apply {
          moveTo(6.2f * u, 10f * u)
          lineTo(9f * u, 4f * u)
          lineTo(11.8f * u, 10f * u)
          moveTo(7.3f * u, 8f * u)
          lineTo(10.7f * u, 8f * u)
        }
      drawPath(a, color, style = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // A plain button: no game, just Dismiss.
    TileMark.BUTTON -> drawRoundRect(color, p(2f, 5.5f), Size(14f * u, 7f * u), CornerRadius(3.5f * u), style = line)
    // A four-pointed sparkle.
    TileMark.SPARKLE -> {
      val c = p(9f, 9f)
      val r = 8f * u
      val star =
        Path().apply {
          moveTo(c.x, c.y - r)
          quadraticTo(c.x, c.y, c.x + r, c.y)
          quadraticTo(c.x, c.y, c.x, c.y + r)
          quadraticTo(c.x, c.y, c.x - r, c.y)
          quadraticTo(c.x, c.y, c.x, c.y - r)
          close()
        }
      drawPath(star, color)
    }
    // A coffee cup with its handle and a little steam.
    TileMark.CAFE -> {
      val cup =
        Path().apply {
          moveTo(2.5f * u, 6f * u)
          lineTo(12.5f * u, 6f * u)
          lineTo(12.5f * u, 11f * u)
          cubicTo(12.5f * u, 14f * u, 10.5f * u, 15.5f * u, 7.5f * u, 15.5f * u)
          cubicTo(4.5f * u, 15.5f * u, 2.5f * u, 14f * u, 2.5f * u, 11f * u)
          close()
        }
      drawPath(cup, color)
      drawArc(color, -90f, 180f, false, p(10.5f, 7.5f), Size(5f * u, 5f * u), style = line)
      drawLine(color, p(6f, 1.8f), p(6f, 3.8f), 1.6f * u, StrokeCap.Round)
      drawLine(color, p(9f, 1.8f), p(9f, 3.8f), 1.6f * u, StrokeCap.Round)
    }
    // A pixel heart.
    TileMark.PIXEL -> {
      val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
      val px = 18f / 7f
      rows.forEachIndexed { row, cells ->
        cells.forEachIndexed { col, cell ->
          if (cell == '1') drawRect(color, p(col * px, 1.5f + row * px), Size(px * u, px * u))
        }
      }
    }
    // One leaf with its midrib.
    TileMark.LEAF -> {
      val leaf =
        Path().apply {
          moveTo(3f * u, 15f * u)
          cubicTo(2f * u, 7f * u, 7f * u, 2.5f * u, 15.5f * u, 2.5f * u)
          cubicTo(15.5f * u, 11f * u, 11f * u, 16f * u, 3f * u, 15f * u)
          close()
        }
      drawPath(leaf, color)
      drawLine(Color.White.copy(alpha = 0.7f), p(4f, 14f), p(12f, 6f), 1.2f * u, StrokeCap.Round)
    }
    // A square wave: the beeps.
    TileMark.WAVE -> {
      val wave =
        Path().apply {
          moveTo(1f * u, 9f * u)
          lineTo(3.5f * u, 9f * u)
          lineTo(3.5f * u, 3f * u)
          lineTo(7f * u, 3f * u)
          lineTo(7f * u, 15f * u)
          lineTo(10.5f * u, 15f * u)
          lineTo(10.5f * u, 3f * u)
          lineTo(14f * u, 3f * u)
          lineTo(14f * u, 9f * u)
          lineTo(17f * u, 9f * u)
        }
      drawPath(wave, color, style = line)
    }
  }
}

package io.github.earthkodyai.rinalarm.alarm.ring

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.character.CharacterAssets
import io.github.earthkodyai.rinalarm.mission.Pad
import io.github.earthkodyai.rinalarm.mission.PadsPhase
import io.github.earthkodyai.rinalarm.mission.PadGrid
import io.github.earthkodyai.rinalarm.mission.PadsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour pads (D17; 3×3 from Hard, G.2) over Rin's half of the ring screen: only her hand shows, gliding in from the top edge as if
 * she sat across the table (the user's pick), pressing each pad of the sequence. Taps count on touch-down, not on
 * release, so a quick tap is never lost. Native Compose only: the game runs the same when her 3D page is missing.
 *
 * @param hand draws her hand with the fingertip at the bottom centre of the given bounds.
 */
@Composable
internal fun PadsBoard(
  state: PadsState,
  onTap: (Pad) -> Unit,
  modifier: Modifier = Modifier,
  hand: @Composable (Modifier) -> Unit = { DrawnHand(it) },
) {
  val grid = state.grid
  BoxWithConstraints(
    // Nothing of its own behind the pads: they sit on the ring screen's patterned surface, joined to the sheet (UX.4).
    modifier.clipToBounds().testTag(PADS_BOARD_TAG),
    contentAlignment = Alignment.Center,
  ) {
    val side = minOf(maxWidth, maxHeight) - PANEL_MARGIN * 2
    val gap = if (grid == PadGrid.THREE) 10.dp else 12.dp
    val pad = (side - gap * (grid.size - 1)) / grid.size
    val left = (maxWidth - side) / 2
    val top = (maxHeight - side) / 2
    // Each pad's touch area reaches halfway into the gaps around it, so no tap on the board falls between two pads
    // (G.2: on the 3×3 board's 10 dp gaps such a tap was lost and the next one read as wrong). Presses reach the game
    // during her demo as well: one just after her last press, before the screen has caught up, is the first answer.
    val takesTaps = state.phase == PadsPhase.DEMO || state.phase == PadsPhase.INPUT
    grid.pads.forEach { p ->
      PadTile(
        p,
        lit = state.lit == p,
        takesTaps = takesTaps,
        answering = state.phase == PadsPhase.INPUT,
        onTap = onTap,
        corner = if (grid == PadGrid.THREE) 18.dp else 24.dp,
        inset = gap / 2,
        modifier =
          Modifier.align(Alignment.TopStart)
            .offset(left + (pad + gap) * grid.column(p) - gap / 2, top + (pad + gap) * grid.row(p) - gap / 2)
            .size(pad + gap),
      )
    }
    // The fingertip rests a little below the pad's centre, so the pad's colour shows around her finger. Her hand keeps
    // about its 2×2 size on the smaller 3×3 pads: a hand shrunk to fit one reads as a child's.
    val handWidth = if (grid == PadGrid.THREE) pad * 1.15f else pad * 0.9f
    val handHeight = side
    val target = state.hand?.takeIf { it in grid.pads }
    val tipX = if (target == null) maxWidth / 2 else left + (pad + gap) * grid.column(target) + pad / 2
    // A repeat (Hard and up): she lifts her finger well clear of the pad before pressing it again, so two presses on
    // one pad read as two.
    val lift = if (state.lifting) pad * 0.35f else 0.dp
    val tipY =
      if (target == null) (-8).dp else top + (pad + gap) * grid.row(target) + pad * 0.6f + (if (state.pressing) 6.dp else 0.dp) - lift
    val move = tween<Dp>(state.moveMs.toInt())
    val x by animateDpAsState(tipX - handWidth / 2, move, label = "handX")
    val y by
      animateDpAsState(
        tipY - handHeight,
        when {
          state.pressing -> tween(90)
          state.lifting -> tween((state.moveMs / 2).toInt())
          else -> move
        },
        label = "handY",
      )
    val scale by animateFloatAsState(if (state.pressing) 0.95f else if (state.lifting) 1.06f else 1f, tween(90), label = "handPress")
    // Her forearm fades in over the top fifth instead of being cut by an edge nobody can see.
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black), blendMode = BlendMode.DstIn)
        }
    ) {
      hand(
        Modifier.align(Alignment.TopStart)
          .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
          .size(handWidth, handHeight)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 1f)
          }
      )
    }
  }
}

@Composable
private fun PadTile(
  pad: Pad,
  lit: Boolean,
  takesTaps: Boolean,
  answering: Boolean,
  onTap: (Pad) -> Unit,
  corner: Dp,
  inset: Dp,
  modifier: Modifier,
) {
  val colour = PAD_COLOURS.getValue(pad)
  // Unlit pads are a solid darker shade (not see-through), so the screen's blue never tints them.
  val dim by animateFloatAsState(if (lit) 0f else 0.55f, tween(if (lit) 40 else 180), label = "padLit")
  val name = stringResource(PAD_NAMES.getValue(pad))
  val shape = RoundedCornerShape(corner)
  // On the navy night ground an unlit pad (the blue most of all) nearly vanished: its own colour outlines it there.
  val night = RinTheme.palette.night
  Box(
    modifier
      .pointerInput(takesTaps) { if (takesTaps) detectTapGestures(onPress = { onTap(pad) }) }
      .semantics {
        role = Role.Button
        contentDescription = name
        if (answering) onClick { onTap(pad); true }
      }
      .testTag(padTag(pad))
  ) {
    Box(
      Modifier.padding(inset)
        .fillMaxSize()
        .background(lerp(colour, Color.Black, dim), shape)
        .then(
          when {
            // The white pad lights white: its ring is the ink colour, so the lit one still stands out.
            lit -> Modifier.border(4.dp, if (pad == Pad.WHITE) RinTheme.palette.ink else Color.White, shape)
            night -> Modifier.border(2.dp, colour.copy(alpha = 0.7f), shape)
            else -> Modifier
          }
        )
    )
  }
}

/**
 * The pads' part of the ring screen's sheet: the intro and "Let's play", then whose turn it is and the 3 s for the
 * next tap draining away (the round is in the top bar's score since UX.4).
 */
@Composable
internal fun PadsCard(state: PadsState?, onStart: () -> Unit, started: Boolean = false) {
  // Once "Let's play" is tapped the game's row shows at once, during her intro too, as the cups' does (G.1: the button
  // stayed up through the intro, and the user tapped it again).
  if (!started && (state == null || state.phase == PadsPhase.READY)) {
    GameIntro(R.string.pads_title, R.string.pads_intro, onStart, PADS_START_TAG)
    return
  }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      stringResource(if (state?.phase == PadsPhase.INPUT) R.string.pads_your_turn else R.string.pads_watch),
      style = MaterialTheme.typography.headlineSmall,
      color = RinTheme.palette.ink,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Countdown(state ?: PadsState())
  }
}

@Composable
private fun Countdown(state: PadsState) {
  val left = remember { Animatable(1f) }
  val input = state.phase == PadsPhase.INPUT
  // Restarts on every right tap and every new attempt.
  LaunchedEffect(input, state.round, state.sequence, state.entered) {
    left.snapTo(1f)
    if (input) left.animateTo(0f, tween(state.tapTimeoutMs.toInt(), easing = LinearEasing))
  }
  LinearProgressIndicator(
    progress = { if (input) left.value else 0f },
    modifier = Modifier.fillMaxWidth().height(8.dp),
    color = RinTheme.palette.primary,
    trackColor = RinTheme.palette.line,
  )
}

/**
 * Her hand as rendered from the build's model (tools/character/render-stills.mjs, `hand.webp`: seen from above,
 * fingertip at the bottom centre), or [DrawnHand] in builds without a model.
 */
@Composable
internal fun RinHand(modifier: Modifier) {
  val context = LocalContext.current
  val image by
    produceState<ImageBitmap?>(null) {
      value =
        withContext(Dispatchers.IO) {
          CharacterAssets.hand(context)?.let { path ->
            runCatching { context.assets.open(path).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
          }
        }
    }
  val bitmap = image
  if (bitmap == null) {
    DrawnHand(modifier)
  } else {
    Box(modifier) {
      // Sized to the image itself, so the fade starts at the image's own top edge wherever the hand is.
      Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .fadeTop(),
      )
    }
  }
}

/**
 * The top [fraction] of what this draws fades in from transparent: the rendered arm ends at the image's top edge,
 * which on the lower pads sits in the middle of the screen, so without it the cut shows as a hard line (the user's
 * report, dev-2).
 */
internal fun Modifier.fadeTop(fraction: Float = 0.45f): Modifier =
  graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      drawRect(Brush.verticalGradient(0f to Color.Transparent, fraction to Color.Black), blendMode = BlendMode.DstIn)
    }

/**
 * A plain drawn hand for builds without a model: her right hand from above, palm down, pointing at the user. The
 * forearm runs off the top edge, the curled fingers sit left of the index finger, the thumb right, the fingertip at
 * the bottom centre.
 */
@Composable
internal fun DrawnHand(modifier: Modifier) {
  Canvas(modifier.fadeTop(0.3f)) {
    val w = size.width
    val skin = Color(0xFFF2CBB0)
    val line = Color(0xFF8A6552)
    val stroke = Stroke(width = w * 0.03f)
    fun part(x: Float, y: Float, pw: Float, ph: Float, r: Float) {
      drawRoundRect(skin, Offset(x, y), Size(pw, ph), CornerRadius(r))
      drawRoundRect(line, Offset(x, y), Size(pw, ph), CornerRadius(r), style = stroke)
    }
    val finger = w * 0.16f
    val fingerH = w * 0.5f
    val fingerX = w / 2 - finger / 2
    val palmW = w * 0.62f
    val palmH = w * 0.5f
    val palmX = fingerX + finger * 1.15f - palmW
    val palmTop = size.height - fingerH - palmH * 0.7f
    part(palmX + palmW * 0.1f, 0f, palmW * 0.8f, palmTop + palmH * 0.3f, w * 0.1f)
    part(palmX + palmW * 0.62f, palmTop + palmH * 0.25f, w * 0.26f, palmH * 0.28f, w * 0.1f) // thumb
    part(palmX, palmTop, palmW, palmH, w * 0.2f)
    for (i in 1..3) part(fingerX - i * finger * 0.95f, palmTop + palmH * 0.7f, finger, palmH * 0.34f, finger / 2)
    part(fingerX, size.height - fingerH, finger, fingerH, finger / 2)
  }
}

private val PAD_COLOURS =
  mapOf(
    Pad.RED to Color(0xFFE53935),
    Pad.BLUE to Color(0xFF1E88E5),
    Pad.YELLOW to Color(0xFFFDD835),
    Pad.GREEN to Color(0xFF43A047),
    Pad.PURPLE to Color(0xFF8E24AA),
    Pad.CYAN to Color(0xFF26C6DA),
    Pad.WHITE to Color(0xFFF5F5F5),
    Pad.ORANGE to Color(0xFFFB8C00),
    Pad.PINK to Color(0xFFF06292),
  )

private val PAD_NAMES =
  mapOf(
    Pad.RED to R.string.pad_red,
    Pad.BLUE to R.string.pad_blue,
    Pad.YELLOW to R.string.pad_yellow,
    Pad.GREEN to R.string.pad_green,
    Pad.PURPLE to R.string.pad_purple,
    Pad.CYAN to R.string.pad_cyan,
    Pad.WHITE to R.string.pad_white,
    Pad.ORANGE to R.string.pad_orange,
    Pad.PINK to R.string.pad_pink,
  )

internal const val PADS_BOARD_TAG = "pads_board"
internal const val PADS_START_TAG = "pads_start"

/** Room around the pads on their surface. */
private val PANEL_MARGIN = 12.dp

internal fun padTag(pad: Pad) = "pad_${pad.name.lowercase()}"

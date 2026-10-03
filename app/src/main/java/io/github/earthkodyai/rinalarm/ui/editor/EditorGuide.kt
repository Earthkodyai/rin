package io.github.earthkodyai.rinalarm.ui.editor

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.character.CharacterAssets
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.character.Mood
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.SpotTargets
import io.github.earthkodyai.rinalarm.ui.common.Spotlight
import io.github.earthkodyai.rinalarm.ui.common.sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The parts of the editor the walkthrough points at, and the scrolling page itself ([PAGE], to scroll them into view). */
enum class GuideTarget {
  PAGE,
  TIME,
  DAY_MODE,
  DAYS,
  GAME,
  TRY,
  SOUND,
  SAVE,
}

/**
 * The first alarm, hands-on (the user, 2026-10-02): after the home tour's tap on Add alarm, the editor lights one part
 * at a time and the user works the real control under the light. Next moves on (the user's own taps are the point,
 * but nothing forces a change); Save is the last step and has no Next, as saving is the way out. Skip ends it.
 */
enum class GuideStep(val target: GuideTarget, @StringRes val tip: Int) {
  TIME(GuideTarget.TIME, R.string.guide_time),
  DAY_MODE(GuideTarget.DAY_MODE, R.string.guide_day_mode),
  DAYS(GuideTarget.DAYS, R.string.guide_days),
  GAME(GuideTarget.GAME, R.string.guide_game),
  /** Only while a game is chosen (None has no Try button). Tapping it plays a practice round, then moves on. */
  TRY(GuideTarget.TRY, R.string.guide_try),
  /** Only in a build with the alarm themes (without them there is nothing to choose). */
  SOUND(GuideTarget.SOUND, R.string.guide_sound),
  SAVE(GuideTarget.SAVE, R.string.guide_save),
  ;

  val last: Boolean
    get() = this == SAVE

  /** The step after this one for the draft as it is now, or null after the last. */
  fun next(mission: MissionChoice, hasSounds: Boolean): GuideStep? =
    entries.drop(ordinal + 1).firstOrNull {
      when (it) {
        TRY -> mission != MissionChoice.None
        SOUND -> hasSounds
        else -> true
      }
    }
}

/**
 * The walkthrough over the editor: the dim layer with one open hole on the step's part, and Rin's tip on a card with
 * her face, at the bottom, or at the top while the Save button at the bottom is the one lit.
 */
@Composable
internal fun EditorGuide(
  step: GuideStep,
  targets: SpotTargets<GuideTarget>,
  onNext: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val lit = targets.bounds[step.target]
  Box(modifier.fillMaxSize().testTag(EDITOR_GUIDE_TAG)) {
    Spotlight(holes = listOfNotNull(lit), lit = lit, open = lit, onTap = {})
    val place =
      if (step.last) {
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
      } else {
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
      }
    GuideCard(step, if (step.last) null else onNext, onSkip, place.padding(16.dp))
  }
}

/** Rin's face, her tip, and Skip / Next under it. */
@Composable
private fun GuideCard(step: GuideStep, onNext: (() -> Unit)?, onSkip: () -> Unit, modifier: Modifier) {
  val p = RinTheme.palette
  Row(
    modifier.fillMaxWidth().sticker(fill = p.bubble, radius = 24.dp).padding(14.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    RinFace(Modifier.size(52.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        stringResource(step.tip),
        style = MaterialTheme.typography.titleSmall,
        color = p.ink,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        QuietPillButton(stringResource(R.string.guide_skip), onSkip, Modifier.testTag(GUIDE_SKIP_TAG), height = 40.dp)
        Spacer(Modifier.weight(1f))
        if (onNext != null) {
          PillButton(stringResource(R.string.guide_next), onNext, Modifier.height(44.dp).width(112.dp).testTag(GUIDE_NEXT_TAG))
        }
      }
    }
  }
}

/** Her face, from the build's head-and-shoulders still; a plain pink circle in builds without her model. */
@Composable
private fun RinFace(modifier: Modifier) {
  val context = LocalContext.current
  val face by
    produceState<ImageBitmap?>(null) {
      val path = CharacterAssets.stills(context, Framing.STRIP).let { it[Mood.CHEERFUL] ?: it.values.firstOrNull() }
      value =
        path?.let {
          withContext(Dispatchers.IO) {
            runCatching { context.assets.open(it).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
          }
        }
    }
  Box(modifier.clip(CircleShape).background(RinTheme.palette.rinCard)) {
    face?.let {
      // The still is head and shoulders: the top of it is her face.
      Image(it, contentDescription = null, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.75f), modifier = Modifier.fillMaxSize())
    }
  }
}

internal const val EDITOR_GUIDE_TAG = "editor_guide"
internal const val GUIDE_NEXT_TAG = "guide_next"
internal const val GUIDE_SKIP_TAG = "guide_skip"

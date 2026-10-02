package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.ui.common.sticker
import io.github.earthkodyai.rinalarm.theme.RinTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.RepeatState

/**
 * Repeat after Rin (task 3.5) on the ring screen, in two parts since UX.4: [RepeatCard] in the sheet (the intro and
 * "Let's play", then "Hear again" and "Can't talk right now") and [RepeatPanel] floating over her chest (the sentence,
 * whose turn it is, the mic and the word chips), so the sheet stays small and she stays big (the user, 2026-10-01).
 * The sentence is always shown, so the game works with the sound off. The mic's state is always visible (CLAUDE.md),
 * on top of Android's own indicator.
 */
@Composable
internal fun RepeatCard(
  state: RepeatState?,
  onStart: () -> Unit,
  onHearAgain: () -> Unit,
  onCantTalk: (() -> Unit)?,
  started: Boolean = false,
) {
  // After the pass the in-game row stays (inert, under "Nice work!"): the intro is taller, and switching back to it
  // grew the sheet and shrank her just as she said goodbye (test ring, 2026-10-01).
  // As the pads' (G.1): the game's row from the first tap on "Let's play", through her intro.
  if (!started && (state == null || state.phase == RepeatPhase.READY)) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      GameIntro(R.string.repeat_title, R.string.repeat_intro, onStart, REPEAT_START_TAG)
      onCantTalk?.let { CantTalk(it) }
    }
    return
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    val canReplay = state?.phase == RepeatPhase.LISTENING || state?.phase == RepeatPhase.TAPPING
    HearAgain(canReplay, onHearAgain)
    Spacer(Modifier.weight(1f))
    onCantTalk?.let { CantTalk(it) }
  }
}

/** The sentence, whose turn it is and the word chips, on a sticker over her chest. */
@Composable
internal fun RepeatPanel(
  state: RepeatState?,
  rinSpeaking: Boolean,
  micLevel: Float,
  onTapWord: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  state ?: return
  val p = RinTheme.palette
  CompositionLocalProvider(LocalContentColor provides p.ink) {
    Column(
      modifier.fillMaxWidth().sticker(fill = p.bubble, radius = 24.dp).padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Sentence(state)
      Status(state, rinSpeaking, micLevel)
      if (state.phase == RepeatPhase.TAPPING) Chips(state, onTapWord)
    }
  }
}

/** Replays her line: an outlined pill, greyed while there is nothing to replay. */
@Composable
private fun HearAgain(enabled: Boolean, onHearAgain: () -> Unit) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(22.dp)
  Row(
    Modifier.height(44.dp)
      .alpha(if (enabled) 1f else 0.45f)
      .clip(shape)
      .background(p.card)
      .border(2.dp, p.line, shape)
      .clickable(enabled = enabled, role = Role.Button, onClick = onHearAgain)
      .padding(horizontal = 14.dp)
      .testTag(REPEAT_AGAIN_TAG),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = p.ink, modifier = Modifier.size(18.dp))
    Spacer(Modifier.size(6.dp))
    Text(stringResource(R.string.repeat_hear_again), style = MaterialTheme.typography.labelLarge, color = p.ink)
  }
}

@Composable
private fun CantTalk(onCantTalk: () -> Unit) {
  // Ink, not the theme's pink: pink text on the pink sheet was 4.1:1, under WCAG's 4.5.
  TextButton(onClick = onCantTalk, modifier = Modifier.heightIn(min = 44.dp).testTag(REPEAT_CANT_TALK_TAG)) {
    Text(stringResource(R.string.repeat_cant_talk), color = RinTheme.palette.ink, style = MaterialTheme.typography.labelLarge)
  }
}

/** The sentence; in the tap fallback the words already placed are picked out. */
@Composable
private fun Sentence(state: RepeatState) {
  val sentence = state.sentence ?: return
  val tapping = state.phase == RepeatPhase.TAPPING || (state.phase == RepeatPhase.FEEDBACK && state.chips.isNotEmpty())
  val text =
    if (!tapping) {
      buildAnnotatedString { append(sentence.text) }
    } else {
      buildAnnotatedString {
        sentence.tokens.forEachIndexed { i, word ->
          if (i > 0) append(' ')
          if (i < state.placed) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(word) }
          } else {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(word) }
          }
        }
      }
    }
  Text(
    text,
    style = MaterialTheme.typography.headlineSmall,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().testTag(REPEAT_SENTENCE_TAG),
  )
}

@Composable
private fun Status(state: RepeatState, rinSpeaking: Boolean, micLevel: Float) {
  val listening = state.phase == RepeatPhase.LISTENING
  val line =
    when (state.phase) {
      RepeatPhase.SPEAKING -> if (rinSpeaking) R.string.repeat_listen else R.string.repeat_read
      RepeatPhase.LISTENING -> R.string.repeat_your_turn
      // Her feedback on the try is her own line, in the subtitle over her (task 4.2).
      RepeatPhase.FEEDBACK -> null
      RepeatPhase.TAPPING -> R.string.repeat_tap
      else -> null
    }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    // The mic indicator: only while the mic is open, and it swells with the voice it hears.
    val scale by animateFloatAsState(if (listening) 1f + micLevel * 0.5f else 1f, tween(90), label = "mic")
    Icon(
      painterResource(if (listening) R.drawable.ic_mic else R.drawable.ic_volume),
      contentDescription = if (listening) stringResource(R.string.repeat_mic_on) else null,
      tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
        Modifier.size(24.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
          .alpha(if (listening || state.phase == RepeatPhase.SPEAKING) 1f else 0f)
          .then(if (listening) Modifier.testTag(REPEAT_MIC_TAG) else Modifier),
    )
    Text(
      line?.let { stringResource(it) }.orEmpty(),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (listening || (state.phase == RepeatPhase.FEEDBACK && state.feedback != Feedback.RIGHT && state.tries < state.maxTries)) {
      Text(
        stringResource(R.string.repeat_try, state.tries, state.maxTries),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  // Only while there is something to listen to: in the chips its row is theirs.
  if (state.phase != RepeatPhase.TAPPING) ListenBar(state)
}

/** Drains over the try's time limit while the mic is open. */
@Composable
private fun ListenBar(state: RepeatState) {
  val left = remember { Animatable(1f) }
  val listening = state.phase == RepeatPhase.LISTENING
  LaunchedEffect(listening, state.index, state.tries) {
    left.snapTo(1f)
    if (listening) left.animateTo(0f, tween(state.listenMs.toInt(), easing = LinearEasing))
  }
  LinearProgressIndicator(
    progress = { if (listening) left.value else 0f },
    modifier = Modifier.fillMaxWidth().height(6.dp).alpha(if (listening) 1f else 0f),
    color = RinTheme.palette.primary,
    trackColor = RinTheme.palette.line,
  )
}

/** The shuffled words; a placed one keeps its space (invisible), so nothing jumps under the user's finger. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(state: RepeatState, onTapWord: (Int) -> Unit) {
  // A wrong chip: the row gives a small shake.
  val shake = remember { Animatable(0f) }
  LaunchedEffect(state.wrongTaps) {
    if (state.wrongTaps == 0) return@LaunchedEffect
    for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, tween(40))
  }
  FlowRow(
    Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value }.testTag(REPEAT_CHIPS_TAG),
    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    state.chips.forEachIndexed { i, chip ->
      OutlinedButton(
        onClick = { onTapWord(i) },
        enabled = !chip.used,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier =
          Modifier.heightIn(min = 44.dp).alpha(if (chip.used) 0f else 1f).then(if (chip.used) Modifier.clearAndSetSemantics {} else Modifier),
      ) {
        Text(chip.text, style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}

internal const val REPEAT_START_TAG = "ring_repeat_start"
internal const val REPEAT_AGAIN_TAG = "ring_repeat_again"
internal const val REPEAT_CANT_TALK_TAG = "ring_repeat_cant_talk"
internal const val REPEAT_SENTENCE_TAG = "ring_repeat_sentence"
internal const val REPEAT_MIC_TAG = "ring_repeat_mic"
internal const val REPEAT_CHIPS_TAG = "ring_repeat_chips"

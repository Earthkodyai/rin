package io.github.earthkodyai.rinalarm.alarm.ring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.earthkodyai.rinalarm.mission.Feedback
import io.github.earthkodyai.rinalarm.mission.RepeatPhase
import io.github.earthkodyai.rinalarm.mission.RepeatState

/**
 * Repeat after Rin (task 3.5) under Rin on the ring screen. The card keeps one height through the whole game: a card
 * that resized made her page stall (task 3.4). The sentence is always shown, so the game works with the sound off and
 * in release builds, which have no voice clips until Phase 4. The mic's state is always visible (CLAUDE.md), on top
 * of Android's own indicator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatCard(
  state: RepeatState?,
  rinSpeaking: Boolean,
  micLevel: Float,
  onStart: () -> Unit,
  onHearAgain: () -> Unit,
  onTapWord: (Int) -> Unit,
  onCantTalk: () -> Unit,
) {
  Box(Modifier.fillMaxWidth().height(CARD_HEIGHT)) {
    Column(
      Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state == null || state.phase == RepeatPhase.READY || state.phase == RepeatPhase.PASSED) {
        GameIntro(R.string.repeat_title, R.string.repeat_intro, onStart, REPEAT_START_TAG, enabled = state?.phase != RepeatPhase.PASSED)
        Spacer(Modifier.weight(1f))
        CantTalk(onCantTalk)
        return@Column
      }
      Sentence(state)
      Status(state, rinSpeaking, micLevel)
      // Top-aligned and scrollable: a long sentence's chips must never end up out of reach (smoke ring, 2026-09-29:
      // centred rows taller than this box lost their top row, and the sentence could not be finished).
      Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        if (state.phase == RepeatPhase.TAPPING) Chips(state, onTapWord)
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val canReplay = state.phase == RepeatPhase.LISTENING || state.phase == RepeatPhase.TAPPING
        OutlinedButton(onClick = onHearAgain, enabled = canReplay, modifier = Modifier.height(48.dp).testTag(REPEAT_AGAIN_TAG)) {
          Icon(painterResource(R.drawable.ic_volume), contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text(stringResource(R.string.repeat_hear_again))
        }
        Spacer(Modifier.weight(1f))
        CantTalk(onCantTalk)
      }
    }
  }
}

@Composable
private fun CantTalk(onCantTalk: () -> Unit) {
  TextButton(onClick = onCantTalk, modifier = Modifier.heightIn(min = 48.dp).testTag(REPEAT_CANT_TALK_TAG)) {
    Text(stringResource(R.string.repeat_cant_talk))
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

/** Fits two rows of chips under a two-line sentence (8 words at most, RepeatGameTest). */
private val CARD_HEIGHT = 320.dp

internal const val REPEAT_START_TAG = "ring_repeat_start"
internal const val REPEAT_AGAIN_TAG = "ring_repeat_again"
internal const val REPEAT_CANT_TALK_TAG = "ring_repeat_cant_talk"
internal const val REPEAT_SENTENCE_TAG = "ring_repeat_sentence"
internal const val REPEAT_MIC_TAG = "ring_repeat_mic"
internal const val REPEAT_CHIPS_TAG = "ring_repeat_chips"

package io.github.earthkodyai.rinalarm.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.ring.PracticeActivity
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.RinPage
import io.github.earthkodyai.rinalarm.ui.common.rinCard
import io.github.earthkodyai.rinalarm.ui.common.sticker
import io.github.earthkodyai.rinalarm.setup.rememberRuntimePermissionAction
import io.github.earthkodyai.rinalarm.ui.editor.TileBadge
import io.github.earthkodyai.rinalarm.ui.editor.TileMark

/**
 * "Try the games" (UX.8, D30): a practice round of each game with no alarm, the end of the home tour and an entry in
 * Settings. Repeat after Rin asks for the mic first (the editor's rule: at the moment it is needed); a "no" still
 * plays, with the word chips to tap.
 */
@Composable
fun PracticeScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val launch = { game: MissionType -> context.startActivity(PracticeActivity.intent(context, game)) }
  val askMic = rememberRuntimePermissionAction(Manifest.permission.RECORD_AUDIO) { launch(MissionType.SPEECH) }
  PracticeScreen(
    onPlay = { game ->
      val micMissing =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
      if (game == MissionType.SPEECH && micMissing) askMic() else launch(game)
    },
    onBack = onBack,
  )
}

@Composable
internal fun PracticeScreen(onPlay: (MissionType) -> Unit, onBack: () -> Unit) {
  val p = RinTheme.palette
  RinPage(stringResource(R.string.practice_title), onBack) {
    Text(stringResource(R.string.practice_intro), style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))
    PRACTICE_GAMES.forEach { (game, title, hint) -> GameCard(game, stringResource(title), stringResource(hint)) { onPlay(game) } }
    Column(Modifier.rinCard(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        stringResource(R.string.practice_stop_title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = p.ink,
      )
      Text(stringResource(R.string.practice_stop), style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
    PillButton(stringResource(R.string.practice_done), onBack, Modifier.padding(top = 6.dp).fillMaxWidth().testTag(PRACTICE_PAGE_DONE_TAG))
  }
}

/** The game's mark as on the editor's tiles, its name and what to do, and an arrow: the whole card starts a round. */
@Composable
private fun GameCard(game: MissionType, title: String, hint: String, onClick: () -> Unit) {
  val p = RinTheme.palette
  Row(
    Modifier.fillMaxWidth()
      .sticker(radius = 22.dp)
      .clip(RoundedCornerShape(22.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(16.dp)
      .testTag(PRACTICE_GAME_TAG + game.stored),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TileBadge(TileMark.of(MissionChoice.Only(game)))
    Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = p.ink)
      Text(hint, style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
    Icon(
      painterResource(R.drawable.ic_arrow_back),
      contentDescription = null,
      tint = p.muted,
      modifier = Modifier.padding(start = 12.dp).size(20.dp).rotate(180f),
    )
  }
}

/** The games in the editor's order, each with its name and what a practice round asks. */
private val PRACTICE_GAMES =
  listOf(
    Triple(MissionType.PADS, R.string.pads_title, R.string.practice_hint_pads),
    Triple(MissionType.CUPS, R.string.cups_title, R.string.practice_hint_cups),
    Triple(MissionType.SPEECH, R.string.repeat_title, R.string.practice_hint_speech),
  )

internal const val PRACTICE_GAME_TAG = "practice_game_"
internal const val PRACTICE_PAGE_DONE_TAG = "practice_page_done"

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun PracticeScreenPreview() {
  RinAlarmTheme { PracticeScreen(onPlay = {}, onBack = {}) }
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun PracticeScreenNightPreview() {
  RinAlarmTheme(night = true) { PracticeScreen(onPlay = {}, onBack = {}) }
}

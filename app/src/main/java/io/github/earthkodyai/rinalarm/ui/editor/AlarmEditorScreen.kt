package io.github.earthkodyai.rinalarm.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.AlarmSound
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.ring.MusicTheme
import io.github.earthkodyai.rinalarm.alarm.ring.PracticeActivity
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.mission.AndroidMissionReadiness
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.QuietPillButton
import io.github.earthkodyai.rinalarm.ui.common.RinBackdrop
import io.github.earthkodyai.rinalarm.ui.common.RinTopBar
import io.github.earthkodyai.rinalarm.ui.common.SectionTitle
import io.github.earthkodyai.rinalarm.ui.common.Spot
import io.github.earthkodyai.rinalarm.ui.common.SpotTargets
import io.github.earthkodyai.rinalarm.ui.common.spotTarget
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.durationText
import io.github.earthkodyai.rinalarm.ui.common.missionChoiceName
import io.github.earthkodyai.rinalarm.ui.common.rememberClockText
import io.github.earthkodyai.rinalarm.ui.common.repeatSummary
import io.github.earthkodyai.rinalarm.ui.common.rinSwitchColors
import io.github.earthkodyai.rinalarm.ui.common.sticker
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.ceil

/** Callbacks from the editor's controls; one object so the stateless screen keeps a short signature. */
interface AlarmEditorActions {
  fun setTime(value: LocalTime)

  fun toggleDay(day: DayOfWeek)

  fun setLabel(value: String)

  fun setRampSeconds(value: Int)

  fun setVibrate(value: Boolean)

  fun setSnoozeMinutes(value: Int)

  fun setMaxSnoozes(value: Int)

  fun setMission(value: MissionChoice)

  fun setDifficulty(value: Difficulty)

  fun setScold(value: Boolean)

  /** Picks [value] and plays a few seconds of it; tapping the sound that is playing stops it. */
  fun pickSound(value: AlarmSound)

  /** Makes [type] ready: asks for its permission, or opens Settings once Android won't ask again. */
  fun allowMission(type: MissionType)

  /** A practice round of the chosen game, without leaving the editor's draft. */
  fun tryGame()

  fun save()

  fun delete()
}

@Composable
fun AlarmEditorScreen(alarmId: Long, onClose: () -> Unit, guided: Boolean = false, onGuideDone: () -> Unit = {}) {
  val viewModel =
    hiltViewModel<AlarmEditorViewModel, AlarmEditorViewModel.Factory>(creationCallback = { it.create(alarmId) })
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // The first alarm's walkthrough (the home tour's last step): the step on screen, kept through a rotation.
  var guideStep by rememberSaveable { mutableStateOf(if (guided) GuideStep.TIME else null) }
  val nextGuideStep: () -> Unit = {
    (state as? AlarmEditorUiState.Editing)?.let { guideStep = guideStep?.next(it.draft.mission, it.themes.isNotEmpty()) }
  }
  val skipGuide = {
    guideStep = null
    onGuideDone()
  }
  // D15: a mission's permission is asked when the user picks it, never at install. Once Android stops showing the
  // dialog (denied twice, or "don't ask again"), Allow opens the app's info page instead.
  val activity = LocalActivity.current
  var askedPermission by rememberSaveable { mutableStateOf(false) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      askedPermission = true
      viewModel.refreshMissions()
    }
  val requestMissionPermission = { type: MissionType ->
    val permission = AndroidMissionReadiness.permissionFor(type)
    if (permission == null) {
      viewModel.refreshMissions()
    } else if (askedPermission && activity?.shouldShowRequestPermissionRationale(permission) == false) {
      SettingsLinks.open(activity, CheckId.MISSIONS, xiaomiFamily = false)
    } else {
      permissionLauncher.launch(permission)
    }
  }
  val context = LocalContext.current
  val actions =
    object : AlarmEditorActions {
      override fun setTime(value: LocalTime) = viewModel.setTime(value)

      override fun toggleDay(day: DayOfWeek) = viewModel.toggleDay(day)

      override fun setLabel(value: String) = viewModel.setLabel(value)

      override fun setRampSeconds(value: Int) = viewModel.setRampSeconds(value)

      override fun setVibrate(value: Boolean) = viewModel.setVibrate(value)

      override fun setSnoozeMinutes(value: Int) = viewModel.setSnoozeMinutes(value)

      override fun setMaxSnoozes(value: Int) = viewModel.setMaxSnoozes(value)

      override fun setMission(value: MissionChoice) = viewModel.setMission(value)

      override fun setDifficulty(value: Difficulty) = viewModel.setDifficulty(value)

      override fun setScold(value: Boolean) = viewModel.setScold(value)

      override fun pickSound(value: AlarmSound) = viewModel.pickSound(value)

      override fun allowMission(type: MissionType) {
        requestMissionPermission(type)
      }

      override fun tryGame() {
        val game = viewModel.gameToTry() ?: return
        val (level, scold) = viewModel.practiceOptions() ?: return
        viewModel.stopPreview()
        context.startActivity(PracticeActivity.intent(context, game, level, scold))
        // The walkthrough's Try step is done once the round opens; the user comes back to the next one.
        if (guideStep == GuideStep.TRY) nextGuideStep()
      }

      override fun save() = viewModel.save()

      override fun delete() = viewModel.delete()
    }

  LifecycleResumeEffect(viewModel) {
    viewModel.refreshMissions()
    // A preview never plays on behind another app, the lock screen or a ringing alarm.
    onPauseOrDispose { viewModel.stopPreview() }
  }
  val savedText =
    (state as? AlarmEditorUiState.Saved)?.ringsIn?.let { stringResource(R.string.alarm_set_toast, durationText(it)) }
  LaunchedEffect(state) {
    when (state) {
      is AlarmEditorUiState.Saved -> {
        savedText?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        // The first alarm is set: the tour is over (home then plays her "alarm set" line).
        if (guided) onGuideDone()
        onClose()
      }
      AlarmEditorUiState.Deleted,
      AlarmEditorUiState.NotFound -> onClose()
      else -> Unit
    }
  }

  AlarmEditorScreen(state, actions, onClose, guide = guideStep, onGuideNext = nextGuideStep, onGuideSkip = skipGuide)
}

@Composable
internal fun AlarmEditorScreen(
  state: AlarmEditorUiState,
  actions: AlarmEditorActions,
  onClose: () -> Unit,
  guide: GuideStep? = null,
  onGuideNext: () -> Unit = {},
  onGuideSkip: () -> Unit = {},
) {
  val editing = state as? AlarmEditorUiState.Editing
  val targets = remember { SpotTargets<GuideTarget>() }
  val scroll = rememberScrollState()
  val density = LocalDensity.current
  // Each step scrolls its part to the top of the page, clear of the tip card at the bottom (Save stays where it is).
  LaunchedEffect(guide, editing != null) {
    if (guide == null || guide.last || editing == null) return@LaunchedEffect
    withFrameNanos {}
    val page = targets.bounds[GuideTarget.PAGE]?.bounds ?: return@LaunchedEffect
    val part = targets.bounds[guide.target]?.bounds ?: return@LaunchedEffect
    scroll.animateScrollBy(part.top - page.top - with(density) { GUIDE_TOP_GAP.toPx() })
  }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }

  val leave = {
    if (editing?.hasChanges == true) {
      confirmDiscard = true
    } else {
      onClose()
    }
  }
  BackHandler(enabled = editing?.hasChanges == true) { confirmDiscard = true }

  val p = RinTheme.palette
  Box(Modifier.fillMaxSize().background(p.ground)) {
    RinBackdrop(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      RinTopBar(stringResource(if (editing?.isNew == false) R.string.editor_title_edit else R.string.editor_title_new), leave)
      if (editing != null) {
        EditorContent(editing, actions, onDelete = { confirmDelete = true }, Modifier.weight(1f), targets, scroll)
      } // else loading, or closing after a save/delete
    }
    if (editing != null) {
      // The ground fades in behind the pill, so the controls scrolling under it never show around it.
      Box(
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Brush.verticalGradient(0f to p.ground.copy(alpha = 0f), 0.3f to p.ground))
          .navigationBarsPadding()
          .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp)
      ) {
        PillButton(
          stringResource(R.string.editor_save),
          actions::save,
          Modifier.fillMaxWidth().spotTarget(targets, GuideTarget.SAVE, radius = Spot.PILL, depth = 5.dp),
          enabled = !editing.busy,
        )
      }
      guide?.let { EditorGuide(it, targets, onGuideNext, onGuideSkip) }
    }
  }

  if (confirmDiscard) {
    AlertDialog(
      onDismissRequest = { confirmDiscard = false },
      title = { Text(stringResource(R.string.discard_title)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDiscard = false
            onClose()
          }
        ) {
          Text(stringResource(R.string.discard_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.discard_keep)) }
      },
    )
  }
  if (editing != null && confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.delete_title)) },
      text = { Text(stringResource(R.string.delete_text, rememberClockText()(editing.draft.time).toString())) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            actions.delete()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = p.danger),
        ) {
          Text(stringResource(R.string.delete_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.editor_cancel)) }
      },
    )
  }
}

@Composable
private fun EditorContent(
  state: AlarmEditorUiState.Editing,
  actions: AlarmEditorActions,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  targets: SpotTargets<GuideTarget>? = null,
  scroll: ScrollState = rememberScrollState(),
) {
  val p = RinTheme.palette
  val draft = state.draft
  Column(
    modifier
      .fillMaxWidth()
      .spotTarget(targets, GuideTarget.PAGE, radius = 0.dp)
      .verticalScroll(scroll)
      .padding(horizontal = 16.dp)
      .padding(top = 8.dp, bottom = SAVE_BUTTON_ROOM)
      .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (state.failed) {
      Text(stringResource(R.string.editor_failed), color = p.danger, style = MaterialTheme.typography.bodyMedium)
    }

    // The time: the wheels in a card, and when it will ring.
    Column(
      Modifier.fillMaxWidth().spotTarget(targets, GuideTarget.TIME, radius = 28.dp, depth = 4.dp).sticker(radius = 28.dp).padding(vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TimeWheels(draft.time, actions::setTime)
      state.ringsIn?.let {
        Text(
          stringResource(R.string.rings_in, durationText(it)),
          style = MaterialTheme.typography.bodyMedium,
          color = p.muted,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    }

    SectionTitle(stringResource(R.string.editor_repeat))
    Box(Modifier.spotTarget(targets, GuideTarget.DAYS, radius = Spot.PILL)) { RepeatDayPicker(draft.repeatDays, actions::toggleDay) }
    Text(repeatSummary(draft.repeatDays), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(start = 4.dp))

    OutlinedTextField(
      value = draft.label,
      onValueChange = actions::setLabel,
      label = { Text(stringResource(R.string.editor_label)) },
      supportingText = {
        Text(stringResource(R.string.editor_label_count, draft.label.length, RingChoices.LABEL_MAX))
      },
      singleLine = true,
      shape = RoundedCornerShape(18.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = p.card,
          unfocusedContainerColor = p.card,
          focusedBorderColor = p.primary,
          unfocusedBorderColor = p.line,
        ),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )

    SectionTitle(stringResource(R.string.editor_mission))
    MissionEditor(state, actions, targets)

    SectionTitle(stringResource(R.string.editor_ringing))
    RingOptionsEditor(draft.ring, state.themes, state.previewing, actions, targets)

    if (!state.isNew) {
      // Outlined, not filled: never the button a thumb lands on by mistake; the dialog asks again.
      val shape = RoundedCornerShape(26.dp)
      Box(
        Modifier.padding(top = 14.dp)
          .fillMaxWidth()
          .height(52.dp)
          .clip(shape)
          .border(2.dp, p.danger, shape)
          .clickable(enabled = !state.busy, role = Role.Button, onClick = onDelete),
        contentAlignment = Alignment.Center,
      ) {
        Text(stringResource(R.string.editor_delete), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = p.danger)
      }
    }
  }
}

/** Rin picks / each game / None as tiles, two to a row, what the choice means, and what stops it on this phone. */
@Composable
private fun MissionEditor(state: AlarmEditorUiState.Editing, actions: AlarmEditorActions, targets: SpotTargets<GuideTarget>?) {
  val p = RinTheme.palette
  val choice = state.draft.mission
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(Modifier.spotTarget(targets, GuideTarget.GAME, radius = 18.dp, depth = 3.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    RingChoices.MISSIONS.chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pair.forEach { option ->
          ChoiceTile(
            missionChoiceName(option),
            selected = option == choice,
            onClick = { actions.setMission(option) },
            Modifier.weight(1f),
            mark = TileMark.of(option),
          )
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    }
    Text(
      stringResource(
        when (choice) {
          MissionChoice.RinPicks -> R.string.mission_hint_rin_picks
          MissionChoice.None -> R.string.mission_hint_none
          is MissionChoice.Only ->
            when (choice.type) {
              MissionType.PADS -> R.string.mission_hint_pads
              MissionType.CUPS -> R.string.mission_hint_cups
              MissionType.SPEECH -> R.string.mission_hint_speech
            }
        }
      ),
      style = MaterialTheme.typography.bodySmall,
      color = p.muted,
      modifier = Modifier.padding(start = 4.dp),
    )
    if (choice != MissionChoice.None) GameOptions(state.draft, actions)
    // A practice round right here (the user, 2026-10-02): the chosen game, or Rin's pick for the next ring.
    if (choice != MissionChoice.None) {
      QuietPillButton(
        stringResource(R.string.editor_try_game),
        actions::tryGame,
        Modifier.fillMaxWidth().spotTarget(targets, GuideTarget.TRY, radius = Spot.PILL).testTag(EDITOR_TRY_TAG),
        height = 46.dp,
        enabled = !state.busy,
      )
    }
    state.missionProblems.forEach { (type, readiness) -> MissionProblem(type, readiness, actions) }
  }
}

/**
 * The game's level and the scold switch (G.1), in one card under the game tiles. Repeat after Rin has one level, so it
 * says so in place of the row; Nightmare carries a warning, as it is made to be lost.
 */
@Composable
private fun GameOptions(draft: Alarm, actions: AlarmEditorActions) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth().sticker(radius = 22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OptionLabel(stringResource(R.string.editor_level))
    if (draft.mission == MissionChoice.Only(MissionType.SPEECH)) {
      Text(stringResource(R.string.level_one), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.testTag(LEVEL_ONE_TAG))
    } else {
      // Two by two: four in a row cut "Nightmare" off on a narrow phone.
      Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RingChoices.LEVELS.chunked(2).forEach { pair ->
          PillChoiceRow(options = pair, selected = draft.difficulty, label = { levelName(it) }, onSelect = actions::setDifficulty)
        }
      }
      if (draft.mission == MissionChoice.RinPicks) {
        Text(stringResource(R.string.level_rin_picks), style = MaterialTheme.typography.bodySmall, color = p.muted)
      }
      if (draft.difficulty == Difficulty.NIGHTMARE) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag(NIGHTMARE_WARNING_TAG)) {
          Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = p.danger, modifier = Modifier.size(18.dp))
          Text(
            stringResource(R.string.level_nightmare_warning),
            style = MaterialTheme.typography.bodySmall,
            color = p.ink,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
    }
    // The whole row toggles: a bigger target than the switch alone.
    Row(
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .toggleable(value = draft.scold, role = Role.Switch, onValueChange = actions::setScold)
        .padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OptionLabel(stringResource(R.string.editor_scold))
        Text(stringResource(R.string.editor_scold_summary), style = MaterialTheme.typography.bodySmall, color = p.muted)
      }
      Switch(
        checked = draft.scold,
        onCheckedChange = null,
        colors = rinSwitchColors(),
        modifier = Modifier.padding(start = 12.dp).testTag(SCOLD_TAG),
      )
    }
  }
}

@Composable
private fun levelName(level: Difficulty): String =
  stringResource(
    when (level) {
      Difficulty.EASY -> R.string.level_easy
      Difficulty.NORMAL -> R.string.level_normal
      Difficulty.HARD -> R.string.level_hard
      Difficulty.NIGHTMARE -> R.string.level_nightmare
    }
  )

/**
 * One choice of several: a tile with a thick pink ring when it is the one, its [mark] on a badge beside the name
 * (minimal marks, the user 2026-10-02). While its sound is [playing], a speaker takes the mark's place.
 */
@Composable
private fun ChoiceTile(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  mark: TileMark? = null,
  playing: Boolean = false,
) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(18.dp)
  val style =
    MaterialTheme.typography.titleSmall.copy(
      fontSize = 15.sp,
      lineHeight = 17.sp,
      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
    )
  val measurer = rememberTextMeasurer()
  val playingText = stringResource(R.string.sound_playing)
  BoxWithConstraints(
    modifier
      .height(60.dp)
      .sticker(radius = 18.dp, depth = 3.dp, outline = null)
      .border(if (selected) 3.dp else 2.dp, if (selected) p.primary else p.line, shape)
      .clip(shape)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .semantics { if (playing) stateDescription = playingText }
      .padding(horizontal = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    // As wide as the name's longest line: a wrapped name ("Magic / morning") would otherwise take the whole width
    // and push the mark and name off centre (the user, 2026-10-02).
    val room = (maxWidth - if (mark != null) TILE_BADGE + MARK_GAP else 0.dp).coerceAtLeast(0.dp)
    val width =
      with(LocalDensity.current) {
        val layout = measurer.measure(text, style, maxLines = 2, constraints = Constraints(maxWidth = room.roundToPx()))
        ceil((0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }).toDp()
      }
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (mark != null) {
        if (playing) PlayingBadge(mark) else TileBadge(mark)
        Spacer(Modifier.width(MARK_GAP))
      }
      Text(
        text,
        style = style,
        color = if (selected) p.primary else p.ink,
        // Two lines: the sound tiles sit in a card, and "Arcade morning" beside its badge does not fit one.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
      )
    }
  }
}

private val MARK_GAP = 8.dp

@Composable
private fun MissionProblem(type: MissionType, readiness: Readiness, actions: AlarmEditorActions) {
  val p = RinTheme.palette
  Row(
    Modifier.fillMaxWidth().sticker(radius = 18.dp, depth = 3.dp).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag(MISSION_PROBLEM_TAG),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = p.danger, modifier = Modifier.size(20.dp))
    Text(
      stringResource(
        when (readiness) {
          Readiness.NO_SENSOR ->
            when (type) {
              MissionType.SPEECH -> R.string.mission_no_mic
              else -> R.string.mission_no_sensor
            }
          else -> R.string.mission_needs_permission
        },
        missionChoiceName(MissionChoice.Only(type)),
      ),
      style = MaterialTheme.typography.bodySmall,
      color = p.ink,
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(vertical = 8.dp),
    )
    if (readiness == Readiness.NO_PERMISSION) {
      TextButton(onClick = { actions.allowMission(type) }) { Text(stringResource(R.string.mission_allow)) }
    }
  }
}

/**
 * The alarm's sound (UX.7): Rin picks (a different theme each day), one theme, or the beep, as tiles two to a row. A
 * pinned theme this build lacks still shows as chosen, so opening the editor never changes it silently. A tap also
 * plays a few seconds of the sound, and the tile [previewing] shows a speaker meanwhile.
 */
@Composable
private fun SoundChooser(sound: AlarmSound, themes: List<MusicTheme>, previewing: AlarmSound?, onPick: (AlarmSound) -> Unit) {
  val p = RinTheme.palette
  val choices = listOf<AlarmSound>(AlarmSound.RinPicks) + themes.map { AlarmSound.Theme(it.id) } + AlarmSound.Beep
  val rinPicks = stringResource(R.string.sound_rin_picks)
  val beep = stringResource(R.string.sound_beep)
  fun name(choice: AlarmSound) =
    when (choice) {
      AlarmSound.RinPicks -> rinPicks
      AlarmSound.Beep -> beep
      is AlarmSound.Theme -> themes.firstOrNull { it.id == choice.id }?.name ?: choice.id
    }
  OptionLabel(stringResource(R.string.editor_sound))
  Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    choices.chunked(2).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { choice ->
          ChoiceTile(name(choice), sound == choice, { onPick(choice) }, Modifier.weight(1f), TileMark.of(choice), previewing == choice)
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
  if (sound == AlarmSound.RinPicks) {
    Text(stringResource(R.string.sound_hint_rin_picks), style = MaterialTheme.typography.bodySmall, color = p.muted)
  }
}

/** How it rings, in one card: the sound, gentle start, vibrate, how many snoozes and how long each. */
@Composable
private fun RingOptionsEditor(
  ring: RingOptions,
  themes: List<MusicTheme>,
  previewing: AlarmSound?,
  actions: AlarmEditorActions,
  targets: SpotTargets<GuideTarget>? = null,
) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth().sticker(radius = 22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // A build without the music only beeps: nothing to choose.
    if (themes.isNotEmpty()) {
      Column(Modifier.spotTarget(targets, GuideTarget.SOUND, radius = 18.dp, depth = 3.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SoundChooser(ring.sound, themes, previewing, actions::pickSound)
      }
    }

    OptionLabel(stringResource(R.string.editor_ramp))
    val rampOff = stringResource(R.string.ramp_off)
    PillChoiceRow(
      options = RingChoices.withCurrent(RingChoices.RAMP_SECONDS, ring.rampSeconds),
      selected = ring.rampSeconds,
      label = { if (it == 0) rampOff else stringResource(R.string.duration_seconds, it) },
      onSelect = actions::setRampSeconds,
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      OptionLabel(stringResource(R.string.editor_vibrate), Modifier.weight(1f))
      Switch(
        checked = ring.vibrate,
        onCheckedChange = actions::setVibrate,
        colors = rinSwitchColors(),
        modifier = Modifier.testTag(VIBRATE_TAG),
      )
    }

    OptionLabel(stringResource(R.string.editor_max_snoozes))
    PillChoiceRow(
      options = RingChoices.withCurrent(RingChoices.MAX_SNOOZES, ring.maxSnoozes),
      selected = ring.maxSnoozes,
      label = { it.toString() },
      onSelect = actions::setMaxSnoozes,
    )

    // Length only matters while snoozing is allowed; greyed out rather than hidden so the layout stays put.
    val snoozeAllowed = ring.maxSnoozes > 0
    OptionLabel(stringResource(R.string.editor_snooze_length), color = if (snoozeAllowed) p.ink else p.muted)
    PillChoiceRow(
      options = RingChoices.withCurrent(RingChoices.SNOOZE_MINUTES, ring.snoozeMinutes),
      selected = ring.snoozeMinutes,
      label = { stringResource(R.string.duration_minutes, it) },
      onSelect = actions::setSnoozeMinutes,
      enabled = snoozeAllowed,
    )
  }
}

@Composable
private fun OptionLabel(text: String, modifier: Modifier = Modifier, color: Color = RinTheme.palette.ink) {
  Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = color, modifier = modifier)
}

/** Seven round toggles, starting on the locale's first day of the week; the alarm's days are filled pink. */
@Composable
private fun RepeatDayPicker(days: RepeatDays, onToggle: (DayOfWeek) -> Unit) {
  val p = RinTheme.palette
  val firstDay = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    for (offset in 0L until 7L) {
      val day = firstDay.plus(offset)
      val selected = day in days
      val name = day.displayName(TextStyle.FULL)
      Box(
        Modifier.size(44.dp)
          .clip(CircleShape)
          .background(if (selected) p.primary else p.card)
          .border(2.dp, if (selected) p.primary else p.line, CircleShape)
          .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(day) })
          .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          day.displayName(TextStyle.NARROW),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
          color = if (selected) p.onPrimary else p.muted,
          modifier = Modifier.clearAndSetSemantics {},
        )
      }
    }
  }
}

/** Room under the last control for the Save pill. */
private val SAVE_BUTTON_ROOM = 110.dp

internal const val VIBRATE_TAG = "editor_vibrate"
internal const val MISSION_PROBLEM_TAG = "editor_mission_problem"
internal const val EDITOR_TRY_TAG = "editor_try_game"
internal const val LEVEL_ONE_TAG = "editor_level_one"
internal const val NIGHTMARE_WARNING_TAG = "editor_nightmare_warning"
internal const val SCOLD_TAG = "editor_scold"

/** Where the walkthrough scrolls each part to: just under the top of the page. */
private val GUIDE_TOP_GAP = 12.dp

private object PreviewActions : AlarmEditorActions {
  override fun setTime(value: LocalTime) = Unit

  override fun toggleDay(day: DayOfWeek) = Unit

  override fun setLabel(value: String) = Unit

  override fun setRampSeconds(value: Int) = Unit

  override fun setVibrate(value: Boolean) = Unit

  override fun setSnoozeMinutes(value: Int) = Unit

  override fun setMaxSnoozes(value: Int) = Unit

  override fun setMission(value: MissionChoice) = Unit

  override fun setDifficulty(value: Difficulty) = Unit

  override fun setScold(value: Boolean) = Unit

  override fun pickSound(value: AlarmSound) = Unit

  override fun allowMission(type: MissionType) = Unit

  override fun tryGame() = Unit

  override fun save() = Unit

  override fun delete() = Unit
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun AlarmEditorPreview() {
  val alarm = Alarm(id = 1, time = LocalTime.of(6, 30), repeatDays = RepeatDays.WEEKDAYS, label = "Work")
  RinAlarmTheme {
    AlarmEditorScreen(
      AlarmEditorUiState.Editing(
        alarm,
        isNew = false,
        hasChanges = true,
        ringsIn = Duration.ofMinutes(440),
        busy = false,
      ),
      PreviewActions,
      onClose = {},
    )
  }
}

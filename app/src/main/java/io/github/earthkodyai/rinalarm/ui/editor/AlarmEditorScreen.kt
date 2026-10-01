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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.RingOptions
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.mission.AndroidMissionReadiness
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.mission.Readiness
import io.github.earthkodyai.rinalarm.setup.CheckId
import io.github.earthkodyai.rinalarm.setup.SettingsLinks
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.PillChoiceRow
import io.github.earthkodyai.rinalarm.ui.common.RinBackdrop
import io.github.earthkodyai.rinalarm.ui.common.RinTopBar
import io.github.earthkodyai.rinalarm.ui.common.SectionTitle
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

  /**
   * Makes [type] ready: asks for its permission (or opens Settings once Android won't ask again), or opens its setup
   * (the QR sticker, whose setup asks for the camera itself).
   */
  fun allowMission(type: MissionType)

  fun save()

  fun delete()
}

@Composable
fun AlarmEditorScreen(alarmId: Long, onClose: () -> Unit, onSetUpQr: () -> Unit = {}) {
  val viewModel =
    hiltViewModel<AlarmEditorViewModel, AlarmEditorViewModel.Factory>(creationCallback = { it.create(alarmId) })
  val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    if (type == MissionType.QR) {
      onSetUpQr()
    } else if (permission == null) {
      viewModel.refreshMissions()
    } else if (askedPermission && activity?.shouldShowRequestPermissionRationale(permission) == false) {
      SettingsLinks.open(activity, CheckId.MISSIONS, xiaomiFamily = false)
    } else {
      permissionLauncher.launch(permission)
    }
  }
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

      override fun allowMission(type: MissionType) {
        requestMissionPermission(type)
      }

      override fun save() = viewModel.save()

      override fun delete() = viewModel.delete()
    }

  LifecycleResumeEffect(viewModel) {
    viewModel.refreshMissions()
    onPauseOrDispose {}
  }
  val context = LocalContext.current
  val savedText =
    (state as? AlarmEditorUiState.Saved)?.ringsIn?.let { stringResource(R.string.alarm_set_toast, durationText(it)) }
  LaunchedEffect(state) {
    when (state) {
      is AlarmEditorUiState.Saved -> {
        savedText?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        onClose()
      }
      AlarmEditorUiState.Deleted,
      AlarmEditorUiState.NotFound -> onClose()
      else -> Unit
    }
  }

  AlarmEditorScreen(state, actions, onClose)
}

@Composable
internal fun AlarmEditorScreen(state: AlarmEditorUiState, actions: AlarmEditorActions, onClose: () -> Unit) {
  val editing = state as? AlarmEditorUiState.Editing
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
        EditorContent(editing, actions, onDelete = { confirmDelete = true }, Modifier.weight(1f))
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
        PillButton(stringResource(R.string.editor_save), actions::save, Modifier.fillMaxWidth(), enabled = !editing.busy)
      }
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
) {
  val p = RinTheme.palette
  val draft = state.draft
  Column(
    modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp)
      .padding(top = 8.dp, bottom = SAVE_BUTTON_ROOM)
      .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (state.failed) {
      Text(stringResource(R.string.editor_failed), color = p.danger, style = MaterialTheme.typography.bodyMedium)
    }

    // The time: the wheels in a card, and when it will ring.
    Column(Modifier.fillMaxWidth().sticker(radius = 28.dp).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
    RepeatDayPicker(draft.repeatDays, actions::toggleDay)
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
    MissionEditor(state, actions)

    SectionTitle(stringResource(R.string.editor_ringing))
    RingOptionsEditor(draft.ring, actions)

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
private fun MissionEditor(state: AlarmEditorUiState.Editing, actions: AlarmEditorActions) {
  val p = RinTheme.palette
  val choice = state.draft.mission
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    RingChoices.MISSIONS.chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pair.forEach { option ->
          ChoiceTile(missionChoiceName(option), selected = option == choice, onClick = { actions.setMission(option) }, Modifier.weight(1f))
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
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
              MissionType.QR -> R.string.mission_hint_qr
              MissionType.SPEECH -> R.string.mission_hint_speech
            }
        }
      ),
      style = MaterialTheme.typography.bodySmall,
      color = p.muted,
      modifier = Modifier.padding(start = 4.dp),
    )
    state.missionProblems.forEach { (type, readiness) -> MissionProblem(type, readiness, actions) }
    state.missionOffers.forEach { type -> MissionOffer(type, actions) }
  }
}

/** One choice of several: a tile with a thick pink ring when it is the one. */
@Composable
private fun ChoiceTile(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(18.dp)
  Box(
    modifier
      .height(60.dp)
      .sticker(radius = 18.dp, depth = 3.dp, outline = null)
      .border(if (selected) 3.dp else 2.dp, if (selected) p.primary else p.line, shape)
      .clip(shape)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold),
      color = if (selected) p.primary else p.ink,
      maxLines = 1,
    )
  }
}

/** Not a problem: a mission Rin could add to her picks after its setup. */
@Composable
private fun MissionOffer(type: MissionType, actions: AlarmEditorActions) {
  val p = RinTheme.palette
  Row(
    Modifier.fillMaxWidth().sticker(radius = 18.dp, depth = 3.dp).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag(MISSION_OFFER_TAG),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = p.muted, modifier = Modifier.size(20.dp))
    Text(
      stringResource(R.string.mission_offer_qr),
      style = MaterialTheme.typography.bodySmall,
      color = p.ink,
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    )
    TextButton(onClick = { actions.allowMission(type) }) { Text(stringResource(R.string.mission_set_up)) }
  }
}

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
              MissionType.QR -> R.string.mission_no_camera
              MissionType.SPEECH -> R.string.mission_no_mic
              else -> R.string.mission_no_sensor
            }
          Readiness.NOT_SET_UP -> R.string.mission_not_set_up
          else -> R.string.mission_needs_permission
        },
        missionChoiceName(MissionChoice.Only(type)),
      ),
      style = MaterialTheme.typography.bodySmall,
      color = p.ink,
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(vertical = 8.dp),
    )
    // The QR sticker's setup asks for the camera itself, so both of its problems lead there.
    when {
      type == MissionType.QR && (readiness == Readiness.NO_PERMISSION || readiness == Readiness.NOT_SET_UP) ->
        TextButton(onClick = { actions.allowMission(type) }) { Text(stringResource(R.string.mission_set_up)) }
      readiness == Readiness.NO_PERMISSION ->
        TextButton(onClick = { actions.allowMission(type) }) { Text(stringResource(R.string.mission_allow)) }
    }
  }
}

/** How it rings, in one card: gentle start, vibrate, how many snoozes and how long each. */
@Composable
private fun RingOptionsEditor(ring: RingOptions, actions: AlarmEditorActions) {
  val p = RinTheme.palette
  Column(Modifier.fillMaxWidth().sticker(radius = 22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
internal const val MISSION_OFFER_TAG = "editor_mission_offer"

private object PreviewActions : AlarmEditorActions {
  override fun setTime(value: LocalTime) = Unit

  override fun toggleDay(day: DayOfWeek) = Unit

  override fun setLabel(value: String) = Unit

  override fun setRampSeconds(value: Int) = Unit

  override fun setVibrate(value: Boolean) = Unit

  override fun setSnoozeMinutes(value: Int) = Unit

  override fun setMaxSnoozes(value: Int) = Unit

  override fun setMission(value: MissionChoice) = Unit

  override fun allowMission(type: MissionType) = Unit

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

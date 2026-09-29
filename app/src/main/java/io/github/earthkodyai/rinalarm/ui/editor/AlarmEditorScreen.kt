package io.github.earthkodyai.rinalarm.ui.editor

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.durationText
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.common.repeatSummary
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

  AlarmEditorScreen(state, actions, onClose, openTimePickerFirst = alarmId == AlarmEditorViewModel.NEW_ALARM_ID)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmEditorScreen(
  state: AlarmEditorUiState,
  actions: AlarmEditorActions,
  onClose: () -> Unit,
  openTimePickerFirst: Boolean = false,
) {
  val editing = state as? AlarmEditorUiState.Editing
  var showTimePicker by rememberSaveable { mutableStateOf(openTimePickerFirst) }
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(stringResource(if (editing?.isNew == false) R.string.editor_title_edit else R.string.editor_title_new))
        },
        navigationIcon = {
          IconButton(onClick = leave) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
          }
        },
        actions = {
          TextButton(onClick = actions::save, enabled = editing != null && !editing.busy) {
            Text(stringResource(R.string.editor_save))
          }
        },
      )
    }
  ) { padding ->
    if (editing == null) {
      Box(Modifier.fillMaxSize().padding(padding)) // Loading, or closing after a save/delete.
      return@Scaffold
    }
    EditorContent(
      state = editing,
      actions = actions,
      onPickTime = { showTimePicker = true },
      onDelete = { confirmDelete = true },
      modifier = Modifier.padding(padding),
    )
  }

  if (editing != null && showTimePicker) {
    AlarmTimePickerDialog(
      initial = editing.draft.time,
      onConfirm = {
        actions.setTime(it)
        showTimePicker = false
      },
      onDismiss = { showTimePicker = false },
    )
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
      text = { Text(stringResource(R.string.delete_text, editing.draft.time.format(rememberTimeFormatter()))) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            actions.delete()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
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
  onPickTime: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val draft = state.draft
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.failed) {
      Text(
        stringResource(R.string.editor_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    TextButton(
      onClick = onPickTime,
      modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TIME_BUTTON_TAG),
    ) {
      Text(draft.time.format(rememberTimeFormatter()), style = MaterialTheme.typography.displayLarge)
    }
    state.ringsIn?.let {
      Text(
        stringResource(R.string.rings_in, durationText(it)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }

    SectionTitle(stringResource(R.string.editor_repeat))
    RepeatDayPicker(draft.repeatDays, actions::toggleDay)
    Text(repeatSummary(draft.repeatDays), style = MaterialTheme.typography.bodySmall)

    OutlinedTextField(
      value = draft.label,
      onValueChange = actions::setLabel,
      label = { Text(stringResource(R.string.editor_label)) },
      supportingText = {
        Text(stringResource(R.string.editor_label_count, draft.label.length, RingChoices.LABEL_MAX))
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
      modifier = Modifier.fillMaxWidth(),
    )

    HorizontalDivider()
    SectionTitle(stringResource(R.string.editor_mission))
    MissionEditor(state, actions)

    HorizontalDivider()
    SectionTitle(stringResource(R.string.editor_ringing))
    RingOptionsEditor(draft.ring, actions)

    if (!state.isNew) {
      HorizontalDivider()
      OutlinedButton(
        onClick = onDelete,
        enabled = !state.busy,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.editor_delete))
      }
    }
  }
}

/** Rin picks / each mission / None, what the choice means, and what stops it from running on this phone. */
@Composable
private fun MissionEditor(state: AlarmEditorUiState.Editing, actions: AlarmEditorActions) {
  val choice = state.draft.mission
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val options = RingChoices.MISSIONS
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, option ->
        SegmentedButton(
          selected = option == choice,
          onClick = { actions.setMission(option) },
          shape = SegmentedButtonDefaults.itemShape(index, options.size),
          icon = {},
          label = { Text(missionChoiceName(option), maxLines = 1) },
        )
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
    )
    state.missionProblems.forEach { (type, readiness) -> MissionProblem(type, readiness, actions) }
    state.missionOffers.forEach { type -> MissionOffer(type, actions) }
  }
}

/** Not a problem: a mission Rin could add to her picks after its setup. */
@Composable
private fun MissionOffer(type: MissionType, actions: AlarmEditorActions) {
  Row(Modifier.fillMaxWidth().testTag(MISSION_OFFER_TAG), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(20.dp))
    Text(
      stringResource(R.string.mission_offer_qr),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    )
    TextButton(onClick = { actions.allowMission(type) }) { Text(stringResource(R.string.mission_set_up)) }
  }
}

@Composable
private fun MissionProblem(type: MissionType, readiness: Readiness, actions: AlarmEditorActions) {
  Row(Modifier.fillMaxWidth().testTag(MISSION_PROBLEM_TAG), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      painterResource(R.drawable.ic_warning),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(20.dp),
    )
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
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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

@Composable
private fun missionChoiceName(choice: MissionChoice): String =
  stringResource(
    when (choice) {
      MissionChoice.RinPicks -> R.string.mission_choice_rin_picks
      MissionChoice.None -> R.string.mission_choice_none
      is MissionChoice.Only ->
        when (choice.type) {
          MissionType.PADS -> R.string.mission_choice_pads
          MissionType.CUPS -> R.string.mission_choice_cups
          MissionType.QR -> R.string.mission_choice_qr
          MissionType.SPEECH -> R.string.mission_choice_speech
        }
    }
  )

@Composable
private fun RingOptionsEditor(ring: RingOptions, actions: AlarmEditorActions) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.editor_ramp), style = MaterialTheme.typography.bodyMedium)
    val rampOff = stringResource(R.string.ramp_off)
    ChoiceRow(
      options = RingChoices.withCurrent(RingChoices.RAMP_SECONDS, ring.rampSeconds),
      selected = ring.rampSeconds,
      label = { if (it == 0) rampOff else stringResource(R.string.duration_seconds, it) },
      onSelect = actions::setRampSeconds,
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.editor_vibrate), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
      Switch(checked = ring.vibrate, onCheckedChange = actions::setVibrate, modifier = Modifier.testTag(VIBRATE_TAG))
    }

    Text(stringResource(R.string.editor_max_snoozes), style = MaterialTheme.typography.bodyMedium)
    ChoiceRow(
      options = RingChoices.withCurrent(RingChoices.MAX_SNOOZES, ring.maxSnoozes),
      selected = ring.maxSnoozes,
      label = { it.toString() },
      onSelect = actions::setMaxSnoozes,
    )

    // Length only matters while snoozing is allowed; greyed out rather than hidden so the layout stays put.
    val snoozeAllowed = ring.maxSnoozes > 0
    Text(
      stringResource(R.string.editor_snooze_length),
      style = MaterialTheme.typography.bodyMedium,
      color = if (snoozeAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChoiceRow(
      options = RingChoices.withCurrent(RingChoices.SNOOZE_MINUTES, ring.snoozeMinutes),
      selected = ring.snoozeMinutes,
      label = { stringResource(R.string.duration_minutes, it) },
      onSelect = actions::setSnoozeMinutes,
      enabled = snoozeAllowed,
    )
  }
}

@Composable
private fun ChoiceRow(
  options: List<Int>,
  selected: Int,
  label: @Composable (Int) -> String,
  onSelect: (Int) -> Unit,
  enabled: Boolean = true,
) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        selected = option == selected,
        onClick = { onSelect(option) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
        enabled = enabled,
        icon = {}, // no check mark: five segments on a narrow phone need the room for their text
        label = { Text(label(option), maxLines = 1) },
      )
    }
  }
}

/** Seven round toggles, starting on the locale's first day of the week. */
@Composable
private fun RepeatDayPicker(days: RepeatDays, onToggle: (DayOfWeek) -> Unit) {
  val firstDay = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    for (offset in 0L until 7L) {
      val day = firstDay.plus(offset)
      val selected = day in days
      val name = day.displayName(TextStyle.FULL)
      Surface(
        checked = selected,
        onCheckedChange = { onToggle(day) },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
          if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp).semantics { contentDescription = name },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(day.displayName(TextStyle.NARROW), Modifier.clearAndSetSemantics {})
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
  val pickerState =
    rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
  TimePickerDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.editor_pick_time)) },
    confirmButton = {
      TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
        Text(stringResource(R.string.editor_ok))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
  ) {
    TimePicker(pickerState)
  }
}

internal const val TIME_BUTTON_TAG = "editor_time"
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

@Preview(showBackground = true, heightDp = 900)
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

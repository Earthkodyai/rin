package io.github.earthkodyai.rinalarm.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.earthkodyai.rinalarm.R
import io.github.earthkodyai.rinalarm.alarm.Alarm
import io.github.earthkodyai.rinalarm.alarm.schedule.RepeatDays
import io.github.earthkodyai.rinalarm.character.CharacterView
import io.github.earthkodyai.rinalarm.character.Framing
import io.github.earthkodyai.rinalarm.character.rememberDefaultMood
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.mission.MissionChoice
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.theme.RinAlarmTheme
import io.github.earthkodyai.rinalarm.theme.RinTheme
import io.github.earthkodyai.rinalarm.theme.onSick
import io.github.earthkodyai.rinalarm.theme.sickFill
import io.github.earthkodyai.rinalarm.ui.common.AppLocale
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.RinBackdrop
import io.github.earthkodyai.rinalarm.ui.common.RinBubble
import io.github.earthkodyai.rinalarm.ui.common.RoundIconButton
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.missionChoiceName
import io.github.earthkodyai.rinalarm.ui.common.rememberClockText
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.common.repeatSummary
import io.github.earthkodyai.rinalarm.ui.common.rinSwitchColors
import io.github.earthkodyai.rinalarm.ui.common.sticker
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
  onAdd: () -> Unit,
  onEdit: (Long) -> Unit,
  onDiagnostics: () -> Unit,
  onSettings: () -> Unit,
  onPractice: () -> Unit,
  viewModel: MainScreenViewModel = hiltViewModel(),
  rin: HomeRinViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val setupIssue by viewModel.setupIssue.collectAsStateWithLifecycle()
  val dayMode by viewModel.dayMode.collectAsStateWithLifecycle()
  val line by rin.line.collectAsStateWithLifecycle()
  val tourPending by viewModel.tourPending.collectAsStateWithLifecycle()
  // The tour (UX.8): which step, kept through a rotation; back to the start whenever it is asked for again.
  var tourIndex by rememberSaveable { mutableIntStateOf(0) }
  val steps = TourStep.steps(hasAlarms = (state as? MainScreenUiState.Success)?.alarms?.isNotEmpty() == true)
  val step = steps.getOrNull(tourIndex).takeIf { tourPending && state is MainScreenUiState.Success }
  val endTour = {
    viewModel.endTour()
    tourIndex = 0
  }
  LaunchedEffect(step) { if (step == TourStep.GAMES) rin.onTourGames() }
  BackHandler(enabled = step != null, onBack = endTour)
  LifecycleResumeEffect(viewModel) {
    viewModel.refreshSetup()
    onPauseOrDispose {}
  }
  LifecycleStartEffect(rin) {
    rin.onShown()
    onStopOrDispose { rin.onHidden() }
  }
  MainScreen(
    state = state,
    onAdd = onAdd,
    onEdit = onEdit,
    onToggle = viewModel::setEnabled,
    setupIssue = setupIssue,
    onDiagnostics = onDiagnostics,
    onSettings = onSettings,
    dayMode = dayMode,
    onDayMode = viewModel::tapDayMode,
    // Her spoken line while she says it, otherwise the tour's tip (text alone: her mouth stays still).
    rinLine = line?.text ?: step?.let { stringResource(it.tip) },
    tour = step?.let { TourState(it, steps.indexOf(it) + 1, steps.size) },
    onTourNext = { tourIndex++ },
    onTourSkip = endTour,
    onTryGames = {
      endTour()
      onPractice()
    },
    character = { modifier ->
      val mood = rememberDefaultMood()
      CharacterView(
        line?.emotion ?: mood,
        modifier,
        framing = Framing.WAIST,
        cues = rin.cues,
        speech = rin.speaking,
        onHeadTap = rin::onHeadTap,
        onVisible = rin::onCharacterVisible,
      )
      // The editor on top takes the panel (and her page) away; her next line then waits for the new page.
      DisposableEffect(rin) { onDispose { rin.onCharacterGone() } }
    },
  )
}

/**
 * Rin's panel: waist up beside her bubble (UX.2). 216 dp, down from the mockup's 290, so the list shows about four
 * alarms on the 14T (the user, 2026-10-01: more room for the alarms, less for her).
 */
private val PANEL_HEIGHT = 216.dp

/** Her share of the panel's width, at its right; the bubble overlaps her left side as on the mockup. */
private const val RIN_WIDTH = 0.64f

/** Room under the last card for the floating button. */
private val ADD_BUTTON_ROOM = 96.dp

@Composable
internal fun MainScreen(
  state: MainScreenUiState,
  onAdd: () -> Unit,
  onEdit: (Long) -> Unit,
  onToggle: (Long, Boolean) -> Unit,
  setupIssue: Boolean = false,
  onDiagnostics: () -> Unit = {},
  onSettings: () -> Unit = {},
  dayMode: DayModeKind? = null,
  onDayMode: (DayModeKind) -> Unit = {},
  rinLine: String? = null,
  tour: TourState? = null,
  onTourNext: () -> Unit = {},
  onTourSkip: () -> Unit = {},
  onTryGames: () -> Unit = {},
  // A slot, so previews and UI tests run without a WebView.
  character: @Composable (Modifier) -> Unit = {},
) {
  val p = RinTheme.palette
  val targets = remember { TourTargets() }
  Box(Modifier.fillMaxSize().background(p.ground)) {
    RinBackdrop(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      TopBar(onDiagnostics, onSettings, targets)
      if (setupIssue) SetupBanner(onDiagnostics)
      RinPanel(rinLine, character, Modifier.tourTarget(targets, TourTarget.PANEL))
      DayModeRow(dayMode, onDayMode, Modifier.tourTarget(targets, TourTarget.DAY_MODE))
      ListHeader(state)
      AlarmList(state, onEdit, onToggle, Modifier.weight(1f).fillMaxWidth(), targets)
    }
    PillButton(
      stringResource(R.string.alarm_add),
      onAdd,
      Modifier.align(Alignment.BottomEnd)
        .navigationBarsPadding()
        .padding(end = 18.dp, bottom = 20.dp)
        .tourTarget(targets, TourTarget.ADD),
      icon = R.drawable.ic_add,
    )
    tour?.let { HomeTour(it.step, it.number, it.count, targets, onTourNext, onTourSkip, onTryGames) }
  }
}

/** The tour's step on screen, and its place among [count] steps. */
data class TourState(val step: TourStep, val number: Int, val count: Int)

@Composable
private fun TopBar(onDiagnostics: () -> Unit, onSettings: () -> Unit, targets: TourTargets) {
  val p = RinTheme.palette
  val name = stringResource(R.string.app_name)
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    // The wordmark: "Rin" in her pink, the rest in ink (the mockups' logo); any other app name stays plain.
    Text(
      buildAnnotatedString {
        if (name.startsWith("Rin")) {
          withStyle(SpanStyle(color = p.primary)) { append("Rin") }
          append(name.removePrefix("Rin"))
        } else {
          append(name)
        }
      },
      style = MaterialTheme.typography.headlineSmall,
      color = p.ink,
      modifier = Modifier.weight(1f).semantics { heading() },
    )
    Row(Modifier.tourTarget(targets, TourTarget.TOP_BUTTONS)) {
      RoundIconButton(R.drawable.ic_pulse, stringResource(R.string.diagnostics_title), onDiagnostics)
      Spacer(Modifier.size(10.dp))
      RoundIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onSettings)
    }
  }
}

/**
 * Stays until the problem is fixed: the one warning, before a morning goes wrong, that an alarm could fail. Deep red
 * in both looks, the only red on the screen, so it reads as important (the user, 2026-10-01).
 */
@Composable
private fun SetupBanner(onClick: () -> Unit) {
  val p = RinTheme.palette
  Row(
    Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
      .fillMaxWidth()
      .sticker(fill = p.alert, radius = 18.dp, depth = 3.dp, shadow = p.alertShadow, outline = null)
      .clip(RoundedCornerShape(18.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = p.onAlert)
    Text(
      stringResource(R.string.setup_banner),
      Modifier.padding(start = 12.dp),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = p.onAlert,
    )
  }
}

/**
 * Rin waist up on her own panel, the sun behind her by day and the moon and twinkling stars at night; the moving
 * backdrop stays on the ground around it (the user, 2026-10-02: in here it was too much). The open side shows the
 * time and date, which fade out while she speaks and her line sits there in a bubble (the mockups).
 */
@Composable
private fun RinPanel(line: String?, character: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(28.dp)
  Box(
    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
      .then(modifier)
      .fillMaxWidth()
      .height(PANEL_HEIGHT)
      .clip(shape)
      .background(p.rinCard)
      .let { if (p.night) it.border(1.dp, p.line, shape) else it }
  ) {
    if (p.night) {
      RinBackdrop(Modifier.fillMaxSize(), meteorRate = 0f)
      Box(Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-30).dp).size(150.dp).background(p.line.copy(alpha = 0.45f), CircleShape))
      Box(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 22.dp).size(70.dp).background(p.glow.copy(alpha = 0.9f), CircleShape))
    } else {
      Box(Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-28).dp).size(120.dp).background(p.glow, CircleShape))
    }
    character(Modifier.align(Alignment.BottomEnd).fillMaxWidth(RIN_WIDTH).fillMaxHeight().padding(top = 8.dp))
    val clockAlpha by animateFloatAsState(if (line == null) 1f else 0f, tween(CLOCK_FADE_MS), label = "panel clock")
    PanelClock(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, bottom = 12.dp).graphicsLayer { alpha = clockAlpha })
    RinBubble(line, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 18.dp))
  }
}

private const val CLOCK_FADE_MS = 300

/**
 * The time, big, and the date under it, kept to the minute. Screen readers skip it: the status bar already says the
 * time, and it is hidden whenever she speaks.
 */
@Composable
private fun PanelClock(modifier: Modifier) {
  val p = RinTheme.palette
  val now by
    produceState(LocalDateTime.now()) {
      while (true) {
        val next = value.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0) + 50)
        value = LocalDateTime.now()
      }
    }
  val clock = rememberClockText()(now.toLocalTime())
  Column(modifier.clearAndSetSemantics {}) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(clock.digits, style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 46.sp), color = p.ink)
      clock.amPm?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
      }
    }
    Text(now.format(PANEL_DATE), style = MaterialTheme.typography.titleSmall, color = p.muted)
  }
}

/** "Fri, Oct 2". */
private val PANEL_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", AppLocale)

/**
 * Rest day and sick day (Phase 5): one tap each, for the next ring; a tap on the one that is on cancels it. Buttons
 * only, nothing to type (the user's slips, Phase 2).
 */
@Composable
private fun DayModeRow(dayMode: DayModeKind?, onTap: (DayModeKind) -> Unit, modifier: Modifier = Modifier) {
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      DayModeKind.entries.forEach { kind -> DayModeButton(kind, dayMode == kind, { onTap(kind) }, Modifier.weight(1f)) }
    }
    if (dayMode != null) {
      Text(
        stringResource(if (dayMode == DayModeKind.REST) R.string.day_rest_on else R.string.day_sick_on),
        style = MaterialTheme.typography.bodySmall,
        color = RinTheme.palette.muted,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
      )
    }
  }
}

@Composable
private fun DayModeButton(kind: DayModeKind, selected: Boolean, onTap: () -> Unit, modifier: Modifier) {
  val p = RinTheme.palette
  val rest = kind == DayModeKind.REST
  val fill = if (!selected) p.card else if (rest) p.primary else p.sickFill
  val content = if (!selected) p.ink else if (rest) p.onPrimary else p.onSick
  val accent = if (rest) p.primary else p.sickFill
  val shape = RoundedCornerShape(24.dp)
  Row(
    modifier
      .height(48.dp)
      .sticker(fill = fill, radius = 24.dp, depth = 3.dp, outline = null)
      .border(2.dp, if (rest) p.candy else p.mint, shape)
      .clip(shape)
      .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onTap() }),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painterResource(if (rest) R.drawable.ic_moon else R.drawable.ic_thermometer),
      contentDescription = null,
      tint = if (selected) content else accent,
      modifier = Modifier.size(18.dp),
    )
    Text(
      stringResource(if (rest) R.string.day_rest else R.string.day_sick),
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
      color = content,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/** "Alarms", and when the soonest one rings ("today", "tomorrow" or the weekday). */
@Composable
private fun ListHeader(state: MainScreenUiState) {
  val p = RinTheme.palette
  val clock = rememberClockText()
  val success = state as? MainScreenUiState.Success
  val next = success?.alarms?.mapNotNull { it.nextRing }?.minOrNull()
  Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(R.string.alarms_title),
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
      color = p.ink,
      modifier = Modifier.weight(1f).semantics { heading() },
    )
    if (next != null) {
      val time = clock(next.toLocalTime()).toString()
      val today = success.today
      Text(
        when (today?.let { next.toLocalDate().toEpochDay() - it.toEpochDay() }) {
          0L -> stringResource(R.string.home_next_today, time)
          1L -> stringResource(R.string.home_next_tomorrow, time)
          else -> stringResource(R.string.home_next_day, next.dayOfWeek.displayName(TextStyle.SHORT), time)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = p.muted,
      )
    }
  }
}

@Composable
private fun AlarmList(
  state: MainScreenUiState,
  onEdit: (Long) -> Unit,
  onToggle: (Long, Boolean) -> Unit,
  modifier: Modifier,
  targets: TourTargets? = null,
) {
  val p = RinTheme.palette
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ADD_BUTTON_ROOM
  when (state) {
    MainScreenUiState.Loading -> Box(modifier) // Blank: Room answers within a frame or two.
    is MainScreenUiState.Error -> Text(stringResource(R.string.alarms_load_error), modifier.padding(20.dp), color = p.ink)
    is MainScreenUiState.Success ->
      if (state.alarms.isEmpty()) {
        Box(modifier) {
          Text(
            stringResource(R.string.alarms_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = p.ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().sticker().padding(20.dp),
          )
        }
      } else {
        LazyColumn(
          modifier,
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          itemsIndexed(state.alarms, key = { _, row -> row.alarm.id }) { index, row ->
            AlarmCard(
              row.alarm,
              onEdit = { onEdit(row.alarm.id) },
              onToggle = { onToggle(row.alarm.id, it) },
              if (index == 0) Modifier.tourTarget(targets, TourTarget.FIRST_ALARM) else Modifier,
            )
          }
        }
      }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(alarm: Alarm, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  val p = RinTheme.palette
  val clock = rememberClockText()(alarm.time)
  // Read out in the locale's own words ("07:00 น."); the card shows digits only.
  val switchDescription = stringResource(R.string.alarm_switch, alarm.time.format(rememberTimeFormatter()))
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier
      .fillMaxWidth()
      .sticker(radius = 22.dp)
      .clip(shape)
      .clickable(onClickLabel = stringResource(R.string.alarm_edit), onClick = onEdit)
      .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          clock.digits,
          style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 38.sp),
          color = if (alarm.enabled) p.ink else p.muted,
          modifier = Modifier.alignByBaseline(),
        )
        clock.amPm?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelLarge,
            color = if (alarm.enabled) p.ink else p.muted,
            modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
          )
        }
        if (alarm.label.isNotBlank()) {
          Text(
            alarm.label,
            style = MaterialTheme.typography.labelLarge,
            color = p.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).alignByBaseline(),
          )
        }
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (alarm.enabled) {
          DayDots(alarm.repeatDays)
        } else {
          Text(stringResource(R.string.alarm_off), style = MaterialTheme.typography.labelLarge, color = p.muted)
        }
        MissionTag(alarm.mission)
      }
    }
    Switch(
      checked = alarm.enabled,
      onCheckedChange = onToggle,
      colors = rinSwitchColors(),
      modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = switchDescription },
    )
  }
}

/**
 * The week from the locale's first day, the alarm's days filled in her pink; read out as one summary ("Weekdays").
 * A one-off alarm says "Once" instead.
 */
@Composable
private fun DayDots(days: RepeatDays) {
  val p = RinTheme.palette
  val summary = repeatSummary(days)
  if (days.isOneShot) {
    Text(summary, style = MaterialTheme.typography.labelLarge, color = p.muted)
    return
  }
  val firstDay = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
  Row(Modifier.clearAndSetSemantics { contentDescription = summary }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    (0L until 7L).map { firstDay.plus(it) }.forEach { day: DayOfWeek ->
      val on = day in days
      Box(
        Modifier.size(22.dp).background(if (on) p.primary else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          day.displayName(TextStyle.NARROW),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
          color = if (on) p.onPrimary else p.muted,
        )
      }
    }
  }
}

/** Which game stops it: a small label in the sun's colour. */
@Composable
private fun MissionTag(mission: MissionChoice) {
  val p = RinTheme.palette
  Text(
    if (mission == MissionChoice.None) stringResource(R.string.home_no_game) else missionChoiceName(mission),
    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
    color = p.onTag,
    modifier = Modifier.background(p.tag, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
  )
}

private val previewRows: List<AlarmRow>
  get() {
    val zone = ZoneId.of("Asia/Bangkok")
    val work = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = RepeatDays.WEEKDAYS, label = "Work", mission = MissionChoice.Only(MissionType.CUPS))
    val gym = Alarm(id = 2, time = LocalTime.of(18, 30), label = "Gym", enabled = false)
    return listOf(AlarmRow(work, ZonedDateTime.of(2026, 9, 29, 7, 0, 0, 0, zone)), AlarmRow(gym, null))
  }

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun MainScreenPreview() {
  RinAlarmTheme {
    MainScreen(MainScreenUiState.Success(previewRows, LocalDate.of(2026, 9, 28)), {}, {}, { _, _ -> }, rinLine = "Good afternoon! Did you eat yet?")
  }
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun MainScreenNightPreview() {
  RinAlarmTheme(night = true) {
    MainScreen(MainScreenUiState.Success(previewRows, LocalDate.of(2026, 9, 28)), {}, {}, { _, _ -> }, dayMode = DayModeKind.REST)
  }
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun MainScreenTourPreview() {
  RinAlarmTheme {
    MainScreen(
      MainScreenUiState.Success(previewRows, LocalDate.of(2026, 9, 28)),
      {},
      {},
      { _, _ -> },
      rinLine = stringResource(R.string.tour_add),
      tour = TourState(TourStep.ADD, 2, 6),
    )
  }
}

@Preview(heightDp = 844, widthDp = 390)
@Composable
private fun MainScreenEmptyPreview() {
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(emptyList()), {}, {}, { _, _ -> }) }
}

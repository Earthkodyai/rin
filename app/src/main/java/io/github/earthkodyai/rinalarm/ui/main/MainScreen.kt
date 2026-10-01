package io.github.earthkodyai.rinalarm.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import io.github.earthkodyai.rinalarm.ui.common.PillButton
import io.github.earthkodyai.rinalarm.ui.common.RoundIconButton
import io.github.earthkodyai.rinalarm.ui.common.displayName
import io.github.earthkodyai.rinalarm.ui.common.missionChoiceName
import io.github.earthkodyai.rinalarm.ui.common.rememberClockText
import io.github.earthkodyai.rinalarm.ui.common.rememberTimeFormatter
import io.github.earthkodyai.rinalarm.ui.common.repeatSummary
import io.github.earthkodyai.rinalarm.ui.common.rinSwitchColors
import io.github.earthkodyai.rinalarm.ui.common.sticker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.ceil

@Composable
fun MainScreen(
  onAdd: () -> Unit,
  onEdit: (Long) -> Unit,
  onDiagnostics: () -> Unit,
  onSettings: () -> Unit,
  viewModel: MainScreenViewModel = hiltViewModel(),
  rin: HomeRinViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val setupIssue by viewModel.setupIssue.collectAsStateWithLifecycle()
  val dayMode by viewModel.dayMode.collectAsStateWithLifecycle()
  val line by rin.line.collectAsStateWithLifecycle()
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
    rinLine = line?.text,
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
  // A slot, so previews and UI tests run without a WebView.
  character: @Composable (Modifier) -> Unit = {},
) {
  val p = RinTheme.palette
  Box(Modifier.fillMaxSize().background(p.ground)) {
    if (p.night) Stars(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      TopBar(onDiagnostics, onSettings)
      if (setupIssue) SetupBanner(onDiagnostics)
      RinPanel(rinLine, character)
      DayModeRow(dayMode, onDayMode)
      ListHeader(state)
      AlarmList(state, onEdit, onToggle, Modifier.weight(1f).fillMaxWidth())
    }
    PillButton(
      stringResource(R.string.alarm_add),
      onAdd,
      Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 20.dp),
      icon = R.drawable.ic_add,
    )
  }
}

@Composable
private fun TopBar(onDiagnostics: () -> Unit, onSettings: () -> Unit) {
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
    RoundIconButton(R.drawable.ic_pulse, stringResource(R.string.diagnostics_title), onDiagnostics)
    Spacer(Modifier.size(10.dp))
    RoundIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onSettings)
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
 * Rin waist up on her own panel, the sun behind her by day and the moon at night. While she speaks her line sits in a
 * bubble at her side (the mockups); home lines are rare, so most of the time it is her alone.
 */
@Composable
private fun RinPanel(line: String?, character: @Composable (Modifier) -> Unit) {
  val p = RinTheme.palette
  val shape = RoundedCornerShape(28.dp)
  Box(
    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
      .fillMaxWidth()
      .height(PANEL_HEIGHT)
      .clip(shape)
      .background(p.rinCard)
      .let { if (p.night) it.border(1.dp, p.line, shape) else it }
  ) {
    if (p.night) {
      Box(Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-30).dp).size(150.dp).background(p.line.copy(alpha = 0.45f), CircleShape))
      Box(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 22.dp).size(70.dp).background(p.glow.copy(alpha = 0.9f), CircleShape))
      Stars(Modifier.fillMaxSize())
    } else {
      Box(Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-28).dp).size(120.dp).background(p.glow, CircleShape))
    }
    character(Modifier.align(Alignment.BottomEnd).fillMaxWidth(RIN_WIDTH).fillMaxHeight().padding(top = 8.dp))
    // Keeps the last line while the bubble fades out.
    var shown by remember { mutableStateOf(line) }
    if (line != null) shown = line
    AnimatedVisibility(
      line != null,
      Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 18.dp),
      enter = fadeIn() + scaleIn(initialScale = 0.85f),
      exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
      Bubble(shown.orEmpty())
    }
  }
}

/**
 * Her line in a rounded bubble with two little dots trailing toward her (the user's pick, 2026-10-01, over tails; drafts
 * in docs/ux/bubble-tails.png). As wide as its longest line, not its widest allowed width, so the padding is even on
 * both sides and the text sits centred (the wrapped text had left a gap on the right).
 */
@Composable
private fun Bubble(text: String) {
  val p = RinTheme.palette
  val style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
  val measurer = rememberTextMeasurer()
  val width =
    with(LocalDensity.current) {
      val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = BUBBLE_TEXT_MAX.roundToPx()))
      val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
      ceil(widest).toDp()
    }
  Text(
    text,
    style = style,
    color = p.ink,
    modifier =
      Modifier.drawBehind { bubbleDots(p.bubble, p.hardShadow, if (p.night) p.line else null) }
        .sticker(fill = p.bubble, depth = 3.dp, shape = BubbleShape)
        .padding(start = 14.dp, end = 14.dp + DOTS_WIDE, top = 11.dp, bottom = 11.dp + DOTS_LOW)
        .width(width)
        .semantics { liveRegion = LiveRegionMode.Polite },
  )
}

private val BUBBLE_TEXT_MAX = 140.dp

/** Room the dots take beside and below the box. */
private val DOTS_WIDE = 18.dp
private val DOTS_LOW = 3.dp

/** The rounded box, leaving room at its right and bottom for the dots. */
private object BubbleShape : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val d = density.density
    val r = 18f * d
    val box = RoundRect(0f, 0f, size.width - DOTS_WIDE.value * d, size.height - DOTS_LOW.value * d, CornerRadius(r, r))
    // A path, not Outline.Rounded: border() draws a rounded outline at the node's full size, dots' room included.
    return Outline.Generic(Path().apply { addRoundRect(box) })
  }
}

/**
 * The two dots off the box's lower right, toward her: a 4.5 dp one just off its side and a 2.8 dp one further out and
 * lower. Their own light 1.5 dp drop, not the box's 3 dp, which made them look like buttons at night.
 */
private fun DrawScope.bubbleDots(fill: Color, shadow: Color, outline: Color?) {
  val d = density
  val bw = size.width - DOTS_WIDE.value * d
  val bh = size.height - DOTS_LOW.value * d
  for ((centre, radius) in listOf(Offset(bw + 6 * d, bh * 0.78f) to 4.5f * d, Offset(bw + 15 * d, bh * 0.98f) to 2.8f * d)) {
    drawCircle(shadow, radius, centre + Offset(0f, 1.5f * d))
    drawCircle(fill, radius, centre)
    if (outline != null) drawCircle(outline, radius, centre, style = Stroke(1f * d))
  }
}

/** A few fixed stars on the night ground (mockup C): decoration, so nothing reads them out. */
@Composable
private fun Stars(modifier: Modifier) {
  val p = RinTheme.palette
  Canvas(modifier.clearAndSetSemantics {}) {
    STARS.forEachIndexed { i, (x, y) ->
      drawCircle(
        if (i % 3 == 2) p.glow else Color.White,
        radius = (if (i % 2 == 0) 1.5f else 1f).dp.toPx(),
        center = Offset(x * size.width, y * size.height),
        alpha = 0.7f,
      )
    }
  }
}

private val STARS = listOf(0.1f to 0.11f, 0.87f to 0.08f, 0.77f to 0.5f, 0.18f to 0.56f, 0.06f to 0.82f, 0.42f to 0.04f, 0.93f to 0.7f)

/**
 * Rest day and sick day (Phase 5): one tap each, for the next ring; a tap on the one that is on cancels it. Buttons
 * only, nothing to type (the user's slips, Phase 2).
 */
@Composable
private fun DayModeRow(dayMode: DayModeKind?, onTap: (DayModeKind) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
          items(state.alarms, key = { it.alarm.id }) { row ->
            AlarmCard(row.alarm, onEdit = { onEdit(row.alarm.id) }, onToggle = { onToggle(row.alarm.id, it) })
          }
        }
      }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(alarm: Alarm, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
  val p = RinTheme.palette
  val clock = rememberClockText()(alarm.time)
  // Read out in the locale's own words ("07:00 น."); the card shows digits only.
  val switchDescription = stringResource(R.string.alarm_switch, alarm.time.format(rememberTimeFormatter()))
  val shape = RoundedCornerShape(22.dp)
  Row(
    Modifier.fillMaxWidth()
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
private fun MainScreenEmptyPreview() {
  RinAlarmTheme { MainScreen(MainScreenUiState.Success(emptyList()), {}, {}, { _, _ -> }) }
}

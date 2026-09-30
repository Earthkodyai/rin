package io.github.earthkodyai.rinalarm.alarm.ring

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.alarm.engine.AlarmEngine
import io.github.earthkodyai.rinalarm.alarm.engine.Ringer
import io.github.earthkodyai.rinalarm.alarm.log.RingEventType
import io.github.earthkodyai.rinalarm.alarm.log.RingLog
import io.github.earthkodyai.rinalarm.alarm.notify.AlarmNotifications
import io.github.earthkodyai.rinalarm.data.AppSettings
import io.github.earthkodyai.rinalarm.data.DayMode
import io.github.earthkodyai.rinalarm.data.DayModeKind
import io.github.earthkodyai.rinalarm.di.AppScope
import io.github.earthkodyai.rinalarm.mission.Hush
import io.github.earthkodyai.rinalarm.mission.MissionPlan
import io.github.earthkodyai.rinalarm.mission.MissionPlanner
import io.github.earthkodyai.rinalarm.mission.MissionReadiness
import io.github.earthkodyai.rinalarm.time.TimeSource
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Rings one alarm: a systemExempted foreground service (S1: allowed for exact-alarm apps and startable from boot,
 * unlike mediaPlayback on Android 15+) with the tone ramping up on USAGE_ALARM, vibration, a full-screen
 * notification, and a 15-minute auto-stop. Runs in Direct Boot.
 *
 * Audio focus: a call (transient loss) pauses the tone and keeps vibrating; the tone resumes when the call ends. A
 * permanent loss (the user starts music) is ignored, so no other app can silence an alarm.
 *
 * Android 15+ refuses focus to an app targeting SDK 36 unless it is on screen, and a foreground service is not enough
 * (1.5: `dumpsys audio` "Focus request DENIED ... procState:4" on every ring). So the tone starts without focus, the
 * request is retried every tick (it succeeds once the ring page is showing), and calls are also detected from the
 * audio mode, which needs no permission and works whether or not focus was ever granted.
 *
 * Missions (task 3.1): the ring's mission is planned here, before the notification is posted, so a ring with a
 * mission offers no Dismiss action there. While the ring screen reports progress the tone drops to
 * RingPolicy.MISSION_QUIET_GAIN and the vibration stops; after RingPolicy.MISSION_IDLE without progress both return.
 */
@AndroidEntryPoint
class RingService : Service() {
  @Inject lateinit var engine: AlarmEngine
  @Inject lateinit var ringLog: RingLog
  @Inject lateinit var ringState: RingState
  @Inject lateinit var notifications: AlarmNotifications
  @Inject @AppScope lateinit var appScope: CoroutineScope
  @Inject lateinit var missionReadiness: MissionReadiness
  @Inject lateinit var time: TimeSource
  @Inject lateinit var settings: AppSettings

  private val handler = Handler(Looper.getMainLooper())
  // One writer thread, so log rows keep the order they were recorded in.
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) private val logOrder = Dispatchers.Default.limitedParallelism(1)
  private var session: Session? = null

  private class Session(
    val request: RingRequest,
    val tone: TonePlayer?,
    val vibrator: AlarmVibrator,
    /** The rest or sick day this ring answers; a Dismiss or auto stop uses it up. */
    val dayMode: DayMode?,
  ) {
    val startedAt = SystemClock.elapsedRealtime()
    var focus: AudioFocusRequest? = null
    /** The last focus request was refused; [watchTick] asks again. */
    var focusRefused = false
    /** Focus is on hold (delayed grant) or lost to a transient owner. */
    var pausedForFocus = false
    var pausedForCall = false
    /** (user's volume, volume we raised it to), or null when we left it alone. */
    var raisedVolume: Pair<Int, Int>? = null
    var wakeLock: PowerManager.WakeLock? = null
    /** Lowered for mission progress (RingPolicy.missionQuiet). */
    var quiet = false
    /** What the game asks for (RingState.hush): quieter while Rin speaks, paused while the mic listens. */
    var hush = Hush.NONE
    var hushJob: Job? = null
    var vibrating = false

    val sick = dayMode?.kind == DayModeKind.SICK

    fun gain(): Float = RingPolicy.toneGain(elapsedMillis(), request.options.rampSeconds, quiet || hush != Hush.NONE, sick)

    fun elapsedMillis() = SystemClock.elapsedRealtime() - startedAt
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_RING -> startRinging(RingRequest.from(intent))
      ACTION_SNOOZE -> snooze()
      ACTION_DISMISS -> dismiss(intent.getStringExtra(EXTRA_SOURCE).orEmpty())
      ACTION_SCREEN -> onScreen(intent.getBooleanExtra(EXTRA_SHOWN, false))
      else -> if (session == null) stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun startRinging(request: RingRequest) {
    val current = session
    if (current != null) {
      // Every startForegroundService call must be answered with startForeground, even when already ringing.
      startForegroundWith(ringState.active.value ?: ActiveRing(current.request, null))
      record(RingEventType.OVERLAP, request.alarmId, request.scheduledAt, "ringing=${current.request.alarmId}")
      return
    }
    val dayMode = readDayMode()
    // A rest or sick day has no game: the plain Dismiss, as with no mission.
    val plan = if (dayMode != null) null else planMission(request)
    // State first: the full-screen activity may start as soon as the notification is posted, and reads it.
    val ring = ActiveRing(request, plan as? MissionPlan.Run, dayMode?.kind)
    ringState.set(ring)
    startForegroundWith(ring)

    val tone = runCatching { TonePlayer() }.getOrNull()
    val s = Session(request, tone, AlarmVibrator(this), dayMode)
    session = s
    s.wakeLock =
      getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RinAlarm:ring").apply {
        acquire(RingPolicy.AUTO_STOP.toMillis() + WAKE_LOCK_MARGIN_MS)
      }
    val volume = raiseVolumeIfLow(s)
    val focus = requestFocus(s)
    s.focusRefused = focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED
    s.pausedForFocus = focus == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
    s.pausedForCall = inCall()
    tone?.setGain(RingPolicy.toneGain(0, request.options.rampSeconds, quiet = false, sick = s.sick))
    updateTone(s)
    updateVibration(s)
    s.hushJob = appScope.launch(Dispatchers.Main.immediate) { ringState.hush.collect { applyHush(s, it) } }
    handler.post(rampTick)
    handler.postDelayed(watchTick, WATCH_TICK_MS)
    handler.postDelayed({ stopRinging(RingEventType.AUTO_STOPPED, "after=${RingPolicy.AUTO_STOP}") }, RingPolicy.AUTO_STOP.toMillis())

    val focusName =
      when (focus) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
        else -> "failed"
      }
    record(
      if (tone != null) RingEventType.RING_START else RingEventType.RING_FAIL,
      request.alarmId,
      request.scheduledAt,
      "tone=${tone != null} focus=$focusName $volume snoozeCount=${request.snoozeCount} late=${request.late}" +
        (if (s.pausedForCall) " call=true" else "") +
        (dayMode?.let { " dayMode=${it.kind.stored}" } ?: ""),
    )
    if (plan is MissionPlan.Unavailable) record(RingEventType.MISSION_UNAVAILABLE, s, plan.reason)
  }

  /**
   * The rest or sick day waiting for this ring, or null. Never throws and waits at most [DAY_MODE_WAIT_MS]: a settings
   * file that cannot be read means an ordinary ring, never a late one.
   */
  private fun readDayMode(): DayMode? =
    runCatching { runBlocking { withTimeoutOrNull(DAY_MODE_WAIT_MS) { settings.dayMode.first() } } }
      .getOrNull()
      ?.takeIf { it.activeAt(time.now().toEpochMilli()) }

  /** Never throws: a failed readiness check means no mission (plain Dismiss), never a ring that cannot stop. */
  private fun planMission(request: RingRequest): MissionPlan =
    runCatching {
        // atZone, not LocalDate.ofInstant: that one is API 34+, and minSdk is 29 (lint NewApi).
        MissionPlanner.plan(request.mission, missionReadiness.check(), time.now().atZone(time.zone()).toLocalDate())
      }
      .getOrElse { MissionPlan.Unavailable("check_failed=${it.javaClass.simpleName}") }

  private fun dismiss(source: String) {
    val s = session ?: return stopSelf()
    if (source == SOURCE_EMERGENCY) record(RingEventType.EMERGENCY_STOP, s, "afterMs=${s.elapsedMillis()}")
    stopRinging(RingEventType.DISMISSED, "source=$source")
  }

  /** Retries refused focus and pauses the tone for calls. Runs until the ring stops. */
  private val watchTick =
    object : Runnable {
      override fun run() {
        val s = session ?: return
        if (s.focusRefused) retryFocus(s)
        updateQuiet(s)
        val call = inCall()
        if (call != s.pausedForCall) {
          s.pausedForCall = call
          updateTone(s)
          record(if (call) RingEventType.TONE_PAUSED else RingEventType.TONE_RESUMED, s, "reason=call")
        }
        handler.postDelayed(this, WATCH_TICK_MS)
      }
    }

  /** Follows the ring screen's mission progress: quiet while it comes in, full again once it stops. */
  private fun updateQuiet(s: Session) {
    val quiet = RingPolicy.missionQuiet(SystemClock.elapsedRealtime(), ringState.lastProgressAt)
    if (quiet == s.quiet) return
    s.quiet = quiet
    s.tone?.setGain(s.gain())
    updateVibration(s)
    record(if (quiet) RingEventType.TONE_QUIET else RingEventType.TONE_FULL, s, "reason=${if (quiet) "mission" else "idle"}")
  }

  /**
   * The game's hush (task 3.5), on top of the rules above: QUIET caps the tone like mission progress so Rin's voice
   * carries; SILENT pauses the tone and the vibration while the mic listens, so neither ends up in what it hears.
   */
  private fun applyHush(s: Session, hush: Hush) {
    if (session !== s || hush == s.hush) return
    val before = s.hush
    s.hush = hush
    s.tone?.setGain(s.gain())
    updateTone(s)
    updateVibration(s)
    if (hush == Hush.SILENT) record(RingEventType.TONE_PAUSED, s, "reason=mic")
    else if (before == Hush.SILENT) record(RingEventType.TONE_RESUMED, s, "reason=mic")
  }

  /** Vibration follows the tone's quiet spells: off while the mission is going well or the game has hushed it. */
  private fun updateVibration(s: Session) {
    val on = s.request.options.vibrate && !s.quiet && s.hush == Hush.NONE
    if (on == s.vibrating) return
    s.vibrating = on
    runCatching { if (on) s.vibrator.start() else s.vibrator.stop() }
  }

  private fun retryFocus(s: Session) {
    val request = s.focus ?: return
    val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
    if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
    s.focusRefused = false
    val delayed = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
    if (delayed) {
      s.pausedForFocus = true
      updateTone(s)
    }
    record(RingEventType.FOCUS_GRANTED, s, "delayed=$delayed afterMs=${s.elapsedMillis()}")
  }

  /** The tone plays unless a call, a transient focus owner or the open mic has it paused; calls never pause vibration. */
  private fun updateTone(s: Session) {
    if (s.pausedForFocus || s.pausedForCall || s.hush == Hush.SILENT) s.tone?.pause() else s.tone?.play()
  }

  /** Ringing for an incoming call, or in a phone or VoIP call. The two newer modes are plain ints on older SDKs. */
  @SuppressLint("InlinedApi")
  private fun inCall(): Boolean =
    getSystemService(AudioManager::class.java).mode in
      setOf(
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING,
        AudioManager.MODE_CALL_REDIRECT,
        AudioManager.MODE_COMMUNICATION_REDIRECT,
      )

  private val rampTick =
    object : Runnable {
      override fun run() {
        val s = session ?: return
        val elapsed = s.elapsedMillis()
        s.tone?.setGain(s.gain())
        if (elapsed < s.request.options.rampSeconds * 1000L) handler.postDelayed(this, RAMP_TICK_MS)
      }
    }

  private fun snooze() {
    val s = session ?: return stopSelf()
    val request = s.request
    if (request.snoozesLeft <= 0) {
      record(RingEventType.SNOOZE_DENIED, request.alarmId, request.scheduledAt, "cap=${request.options.maxSnoozes}")
      return
    }
    appScope.launch {
      if (engine.snooze(request.alarmId, request.snoozeCount) == null) {
        ringLog.record(RingEventType.SNOOZE_DENIED, request.alarmId, request.scheduledAt, "engine refused")
      }
    }
    stopRinging(RingEventType.SNOOZED, "n=${request.snoozeCount + 1}/${request.options.maxSnoozes}")
  }

  private fun stopRinging(type: RingEventType, detail: String) {
    val s = session
    if (s == null) {
      stopSelf()
      return
    }
    session = null
    s.hushJob?.cancel()
    handler.removeCallbacksAndMessages(null)
    s.tone?.release()
    runCatching { s.vibrator.stop() }
    s.focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
    restoreVolume(s)
    s.wakeLock?.takeIf { it.isHeld }?.release()
    ringState.set(null)
    record(type, s.request.alarmId, s.request.scheduledAt, "$detail rangMs=${s.elapsedMillis()}")
    // The day mode covered this ring and its snoozes; a ring that is over for good uses it up.
    val used = s.dayMode
    if (used != null && (type == RingEventType.DISMISSED || type == RingEventType.AUTO_STOPPED)) {
      appScope.launch { runCatching { settings.endDayMode(used) } }
    }
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    // Only reached with a live session if Android killed the service mid-ring.
    if (session != null) stopRinging(RingEventType.RING_FAIL, "destroyed")
    super.onDestroy()
  }

  /** The ring page came up or went away: the notification stops or starts alerting over it. */
  private fun onScreen(shown: Boolean) {
    val ring = ringState.active.value
    if (session == null || ring == null) return
    runCatching {
      getSystemService(NotificationManager::class.java).notify(AlarmNotifications.RINGING_ID, notifications.ringing(ring, shown))
    }
  }

  private fun startForegroundWith(ring: ActiveRing) {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
    } else {
      0
    }
    ServiceCompat.startForeground(this, AlarmNotifications.RINGING_ID, notifications.ringing(ring), type)
  }

  /** User decision (1.2): an alarm stream below 40% is raised for the ring and put back afterwards. */
  private fun raiseVolumeIfLow(s: Session): String {
    val audio = getSystemService(AudioManager::class.java)
    val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val target = RingPolicy.volumeFloorIndex(current, max) ?: return "vol=$current/$max"
    return runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
      .fold(
        onSuccess = {
          s.raisedVolume = current to target
          record(RingEventType.VOLUME_RAISED, s.request.alarmId, s.request.scheduledAt, "$current->$target/$max")
          "vol=$target/$max"
        },
        onFailure = { "vol=$current/$max raiseFailed=${it.javaClass.simpleName}" },
      )
  }

  /** Puts the user's volume back, unless they changed it themselves while it rang. */
  private fun restoreVolume(s: Session) {
    val (original, raisedTo) = s.raisedVolume ?: return
    val audio = getSystemService(AudioManager::class.java)
    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == raisedTo) {
      runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
    }
  }

  private fun requestFocus(s: Session): Int {
    val request =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ALARM_AUDIO)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(
          { change ->
            when (change) {
              AudioManager.AUDIOFOCUS_GAIN ->
                if (s.pausedForFocus) {
                  s.pausedForFocus = false
                  updateTone(s)
                  record(RingEventType.TONE_RESUMED, s, "reason=focus")
                }
              AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                s.pausedForFocus = true
                updateTone(s)
                record(RingEventType.TONE_PAUSED, s, "reason=focus")
              }
            // LOSS (another app took over for good) and CAN_DUCK are ignored: keep ringing at full volume.
            }
          },
          handler,
        )
        .build()
    s.focus = request
    return getSystemService(AudioManager::class.java).requestAudioFocus(request)
  }

  private fun record(type: RingEventType, alarmId: Long, scheduledAt: Instant?, detail: String) {
    appScope.launch(logOrder) { ringLog.record(type, alarmId, scheduledAt, detail) }
  }

  private fun record(type: RingEventType, s: Session, detail: String) =
    record(type, s.request.alarmId, s.request.scheduledAt, detail)

  companion object {
    /** The longest a ring waits to read the day mode (DataStore's first read after a cold start). */
    private const val DAY_MODE_WAIT_MS = 1_000L
    private const val ACTION_RING = "io.github.earthkodyai.rinalarm.action.RING"
    private const val ACTION_SNOOZE = "io.github.earthkodyai.rinalarm.action.SNOOZE"
    private const val ACTION_DISMISS = "io.github.earthkodyai.rinalarm.action.DISMISS"
    private const val EXTRA_SOURCE = "source"
    private const val ACTION_SCREEN = "io.github.earthkodyai.rinalarm.action.SCREEN"
    private const val EXTRA_SHOWN = "shown"
    /** Dismiss sources that RingActivity sends: the mission passed, the emergency hold, or the plain button. */
    const val SOURCE_MISSION = "mission"
    const val SOURCE_EMERGENCY = "emergency"
    const val SOURCE_SCREEN = "screen"
    private const val RAMP_TICK_MS = 200L
    private const val WATCH_TICK_MS = 500L
    private const val WAKE_LOCK_MARGIN_MS = 60_000L

    fun ringIntent(context: Context, request: RingRequest): Intent =
      request.putInto(Intent(context, RingService::class.java).setAction(ACTION_RING))

    fun screenIntent(context: Context, shown: Boolean): Intent =
      Intent(context, RingService::class.java).setAction(ACTION_SCREEN).putExtra(EXTRA_SHOWN, shown)

    fun snoozeIntent(context: Context): Intent = Intent(context, RingService::class.java).setAction(ACTION_SNOOZE)

    fun dismissIntent(context: Context, source: String): Intent =
      Intent(context, RingService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_SOURCE, source)
  }
}

class ServiceRinger @Inject constructor(@ApplicationContext private val context: Context) : Ringer {
  override fun start(request: RingRequest): Boolean =
    runCatching { ContextCompat.startForegroundService(context, RingService.ringIntent(context, request)) }.isSuccess
}

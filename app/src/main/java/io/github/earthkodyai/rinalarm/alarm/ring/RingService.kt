package io.github.earthkodyai.rinalarm.alarm.ring

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
import io.github.earthkodyai.rinalarm.di.AppScope
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rings one alarm: a systemExempted foreground service (S1: allowed for exact-alarm apps and startable from boot,
 * unlike mediaPlayback on Android 15+) with the tone ramping up on USAGE_ALARM, vibration, a full-screen
 * notification, and a 15-minute auto-stop. Runs in Direct Boot.
 *
 * Audio focus: a call (transient loss) pauses the tone and keeps vibrating; the tone resumes when the call ends. A
 * permanent loss (the user starts music) is ignored, so no other app can silence an alarm.
 */
@AndroidEntryPoint
class RingService : Service() {
  @Inject lateinit var engine: AlarmEngine
  @Inject lateinit var ringLog: RingLog
  @Inject lateinit var ringState: RingState
  @Inject lateinit var notifications: AlarmNotifications
  @Inject @AppScope lateinit var appScope: CoroutineScope

  private val handler = Handler(Looper.getMainLooper())
  // One writer thread, so log rows keep the order they were recorded in.
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) private val logOrder = Dispatchers.Default.limitedParallelism(1)
  private var session: Session? = null

  private class Session(val request: RingRequest, val tone: TonePlayer?, val vibrator: AlarmVibrator) {
    val startedAt = SystemClock.elapsedRealtime()
    var focus: AudioFocusRequest? = null
    /** (user's volume, volume we raised it to), or null when we left it alone. */
    var raisedVolume: Pair<Int, Int>? = null
    var wakeLock: PowerManager.WakeLock? = null

    fun elapsedMillis() = SystemClock.elapsedRealtime() - startedAt
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_RING -> startRinging(RingRequest.from(intent))
      ACTION_SNOOZE -> snooze()
      ACTION_DISMISS -> stopRinging(RingEventType.DISMISSED, "source=${intent.getStringExtra(EXTRA_SOURCE)}")
      else -> if (session == null) stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun startRinging(request: RingRequest) {
    val current = session
    if (current != null) {
      // Every startForegroundService call must be answered with startForeground, even when already ringing.
      startForegroundWith(current.request)
      record(RingEventType.OVERLAP, request.alarmId, request.scheduledAt, "ringing=${current.request.alarmId}")
      return
    }
    // State first: the full-screen activity may start as soon as the notification is posted, and reads it.
    ringState.set(request)
    startForegroundWith(request)

    val tone = runCatching { TonePlayer() }.getOrNull()
    val s = Session(request, tone, AlarmVibrator(this))
    session = s
    s.wakeLock =
      getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RinAlarm:ring").apply {
        acquire(RingPolicy.AUTO_STOP.toMillis() + WAKE_LOCK_MARGIN_MS)
      }
    val volume = raiseVolumeIfLow(s)
    val focus = requestFocus(s)
    tone?.setGain(RingPolicy.rampGain(0, request.options.rampSeconds))
    if (focus != AudioManager.AUDIOFOCUS_REQUEST_DELAYED) tone?.play()
    if (request.options.vibrate) runCatching { s.vibrator.start() }
    handler.post(rampTick)
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
      "tone=${tone != null} focus=$focusName $volume snoozeCount=${request.snoozeCount} late=${request.late}",
    )
  }

  private val rampTick =
    object : Runnable {
      override fun run() {
        val s = session ?: return
        val elapsed = s.elapsedMillis()
        s.tone?.setGain(RingPolicy.rampGain(elapsed, s.request.options.rampSeconds))
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
    handler.removeCallbacksAndMessages(null)
    s.tone?.release()
    runCatching { s.vibrator.stop() }
    s.focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
    restoreVolume(s)
    s.wakeLock?.takeIf { it.isHeld }?.release()
    ringState.set(null)
    record(type, s.request.alarmId, s.request.scheduledAt, "$detail rangMs=${s.elapsedMillis()}")
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    // Only reached with a live session if Android killed the service mid-ring.
    if (session != null) stopRinging(RingEventType.RING_FAIL, "destroyed")
    super.onDestroy()
  }

  private fun startForegroundWith(request: RingRequest) {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
    } else {
      0
    }
    ServiceCompat.startForeground(this, AlarmNotifications.RINGING_ID, notifications.ringing(request), type)
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
              AudioManager.AUDIOFOCUS_GAIN -> s.tone?.play()
              AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> s.tone?.pause()
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

  companion object {
    private const val ACTION_RING = "io.github.earthkodyai.rinalarm.action.RING"
    private const val ACTION_SNOOZE = "io.github.earthkodyai.rinalarm.action.SNOOZE"
    private const val ACTION_DISMISS = "io.github.earthkodyai.rinalarm.action.DISMISS"
    private const val EXTRA_SOURCE = "source"
    private const val RAMP_TICK_MS = 200L
    private const val WAKE_LOCK_MARGIN_MS = 60_000L

    fun ringIntent(context: Context, request: RingRequest): Intent =
      request.putInto(Intent(context, RingService::class.java).setAction(ACTION_RING))

    fun snoozeIntent(context: Context): Intent = Intent(context, RingService::class.java).setAction(ACTION_SNOOZE)

    fun dismissIntent(context: Context, source: String): Intent =
      Intent(context, RingService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_SOURCE, source)
  }
}

class ServiceRinger @Inject constructor(@ApplicationContext private val context: Context) : Ringer {
  override fun start(request: RingRequest): Boolean =
    runCatching { ContextCompat.startForegroundService(context, RingService.ringIntent(context, request)) }.isSuccess
}

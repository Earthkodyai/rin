package com.example.s1alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rings with a generated tone (no media file, so it also works in Direct Boot before first
 * unlock) on USAGE_ALARM, as a systemExempted foreground service with a full-screen intent.
 */
class RingService : Service() {
  private var track: AudioTrack? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var current: Alarm? = null
  private val handler = Handler(Looper.getMainLooper())

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopRinging(intent.getStringExtra(EXTRA_REASON) ?: "dismiss")
      return START_NOT_STICKY
    }
    val a = Alarm(
      intent?.getIntExtra("id", -1) ?: -1,
      intent?.getLongExtra("triggerAt", 0) ?: 0,
      intent?.getStringExtra("label") ?: "",
      intent?.getIntExtra("ringSec", 60) ?: 60,
    )
    current = a
    ServiceCompat.startForeground(
      this, NOTIF_ID, buildNotification(a),
      if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
    )
    wakeLock = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "s1:ring")
      .apply { acquire(a.ringSec * 1000L + 5_000) }
    val ok = runCatching { play() }
    RingLog.log(this, if (ok.isSuccess) "ring_start" else "ring_fail", a, ok.exceptionOrNull()?.toString() ?: "")
    handler.removeCallbacksAndMessages(null)
    handler.postDelayed({ stopRinging("timeout") }, a.ringSec * 1000L)
    return START_NOT_STICKY
  }

  private fun play() {
    track?.release()
    val rate = 44_100
    val pcm = tonePattern(rate)
    track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build(),
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(rate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build(),
      )
      .setTransferMode(AudioTrack.MODE_STATIC)
      .setBufferSizeInBytes(pcm.size * 2)
      .build()
      .apply {
        write(pcm, 0, pcm.size)
        setLoopPoints(0, pcm.size, -1)
        play()
      }
  }

  private fun stopRinging(reason: String) {
    handler.removeCallbacksAndMessages(null)
    current?.let { RingLog.log(this, "ring_stop", it, reason) }
    current = null
    track?.runCatching { stop(); release() }
    track = null
    wakeLock?.takeIf { it.isHeld }?.release()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
    sendBroadcast(Intent(RingActivity.ACTION_FINISH).setPackage(packageName))
  }

  override fun onDestroy() {
    track?.runCatching { release() }
    wakeLock?.takeIf { it.isHeld }?.release()
    super.onDestroy()
  }

  private fun buildNotification(a: Alarm): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(null, null)
      },
    )
    val full = PendingIntent.getActivity(
      this, 0,
      Intent(this, RingActivity::class.java).putExtra("id", a.id).putExtra("label", a.label)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val dismiss = PendingIntent.getService(
      this, 1, stopIntent(this, "dismiss_notif"), PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("S1 alarm: ${a.label}")
      .setContentText("Ringing")
      .setCategory(Notification.CATEGORY_ALARM)
      .setOngoing(true)
      .setFullScreenIntent(full, true)
      .setContentIntent(full)
      .addAction(Notification.Action.Builder(null, "Dismiss", dismiss).build())
      .build()
  }

  companion object {
    private const val CHANNEL = "ring"
    private const val NOTIF_ID = 1
    private const val ACTION_STOP = "stop"
    private const val EXTRA_REASON = "reason"

    fun start(ctx: Context, a: Alarm, late: Boolean) {
      val i = Intent(ctx, RingService::class.java)
        .putExtra("id", a.id).putExtra("triggerAt", a.triggerAt)
        .putExtra("label", if (late) "${a.label}(late)" else a.label).putExtra("ringSec", a.ringSec)
      runCatching { ctx.startForegroundService(i) }
        .onFailure { RingLog.log(ctx, "fgs_fail", a, it.toString()) }
    }

    fun stopIntent(ctx: Context, reason: String) =
      Intent(ctx, RingService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_REASON, reason)

    /** 1 s loop: two 880 Hz beeps then a pause. */
    private fun tonePattern(rate: Int): ShortArray {
      val out = ShortArray(rate)
      fun beep(fromMs: Int, toMs: Int) {
        for (i in rate * fromMs / 1000 until rate * toMs / 1000) {
          out[i] = (sin(2 * PI * 880 * i / rate) * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
      }
      beep(0, 200)
      beep(300, 500)
      return out
    }
  }
}

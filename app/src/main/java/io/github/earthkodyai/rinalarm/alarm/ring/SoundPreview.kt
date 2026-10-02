package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The editor's preview of an alarm sound (the user, 2026-10-02): a few seconds of it when its tile is tapped. */
fun interface PreviewSounds {
  /** The theme [themeId], or the beep when null, ready to [RingSound.play]. */
  fun open(themeId: String?): RingSound
}

/**
 * The ring's own players on the media stream: alarms are mostly set at bedtime, so the preview follows the media volume
 * (the user's choice) and never the alarm volume. Other audio pauses while it plays. At full gain it still sounded too
 * quiet (the user, 2026-10-02; the themes are about -15 LUFS), so a LoudnessEnhancer lifts it [BOOST_MB] on its own
 * session, its limiter keeping the peaks from clipping. [RingSound.release] returns at once: MusicPlayer may wait up to
 * half a second for its decoding thread, so that happens off the main thread.
 */
class AndroidPreviewSounds @Inject constructor(@ApplicationContext private val context: Context) : PreviewSounds {
  private val audio = context.getSystemService(AudioManager::class.java)

  override fun open(themeId: String?): RingSound {
    return if (themeId == null) {
      TonePlayer(PREVIEW_AUDIO).let { Focused(it, it.sessionId) }
    } else {
      MusicPlayer(PREVIEW_AUDIO) { context.assets.openFd(AssetMusicCatalog.path(themeId)) }.let { Focused(it, it.sessionId) }
    }
  }

  private inner class Focused(private val sound: RingSound, session: Int) : RingSound by sound {
    // A phone without the effect plays the preview unboosted.
    private val boost =
      runCatching {
          LoudnessEnhancer(session).apply {
            setTargetGain(BOOST_MB)
            enabled = true
          }
        }
        .getOrNull()

    private val focus =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(PREVIEW_AUDIO).build()

    override fun play() {
      audio.requestAudioFocus(focus)
      sound.play()
    }

    override fun release() {
      audio.abandonAudioFocusRequest(focus)
      Thread(
          {
            runCatching { sound.release() }
            boost?.release()
          },
          "RinAlarm-preview-release",
        )
        .start()
    }
  }

  private companion object {
    /** +6 dB, in millibels. */
    const val BOOST_MB = 600

    val PREVIEW_AUDIO: AudioAttributes =
      AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
  }
}

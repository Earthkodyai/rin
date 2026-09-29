package io.github.earthkodyai.rinalarm.character

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A line being spoken: the page moves the mouth along [mouth] from [at], the epoch ms its first sample played. */
data class Speaking(val line: String, val mouth: MouthTrack, val at: Long)

/**
 * Plays Rin's voice lines natively (task 2.4), so the voice never depends on the WebView: if the page is gone, she
 * still speaks and the still image stays. Each line is `<base>.mp3` in assets with an optional `<base>.mouth.json`;
 * without one, the mouth follows the clip's loudness (decoded here). [onSpeaking] gets the line once its audio is
 * actually playing, timed by MediaPlayer's presentation timestamp, and null when it ends or stops. Main thread only.
 *
 * MediaPlayer's prepare(), release() and getTimestamp() block for tens of ms (getTimestamp for 70-90 ms while the
 * audio output starts), so they run off the main thread: the WebView draws through the app's UI thread, and on it
 * they froze Rin for 58-257 ms at the start and end of every line (task 2.5).
 */
class VoicePlayer(
  private val context: Context,
  private val scope: CoroutineScope,
  private val attributes: AudioAttributes = SPEECH,
  private val onSpeaking: (Speaking?) -> Unit,
) {
  private val audio = context.getSystemService(AudioManager::class.java)
  private var player: MediaPlayer? = null
  private var job: Job? = null
  /** Told once how the line in progress ended: true when it played to the end. */
  private var onEnd: ((Boolean) -> Unit)? = null
  private val focus =
    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()

  /**
   * Starts [base] (an asset path without extension, such as `voice/dev/L01`), cutting off any line still playing.
   * [onEnd] hears once how it ended: true when it played to the end, false when there was no clip or it was cut off.
   */
  fun play(base: String, onEnd: ((Boolean) -> Unit)? = null) {
    stop()
    this.onEnd = onEnd
    job =
      scope.launch {
        val mouth = withContext(Dispatchers.IO) { loadMouth(base) }
        if (mouth == null) {
          Log.w(TAG, "say $base: no clip")
          end(false)
          return@launch
        }
        // Not cancellable, so a player prepared just as the line is cut off is still released here, not leaked.
        val mp = withContext(Dispatchers.IO + NonCancellable) { prepare(base) }
        if (mp == null) {
          end(false)
          return@launch
        }
        if (!isActive) {
          releaseOffMain(mp)
          return@launch
        }
        player = mp
        mp.setOnCompletionListener {
          val played = this@VoicePlayer.onEnd
          this@VoicePlayer.onEnd = null
          stop()
          played?.invoke(true)
        }
        audio.requestAudioFocus(focus)
        val started = System.currentTimeMillis()
        mp.start()
        val at = withContext(Dispatchers.IO) { firstSampleAt(mp) } ?: started
        Log.i(TAG, "say $base mouth=${mouth.second} at=start+${at - started}ms timestamp=${at != started}")
        onSpeaking(Speaking(base, mouth.first, at))
      }
  }

  fun stop() {
    job?.cancel()
    job = null
    end(false)
    val mp = player ?: return
    player = null
    releaseOffMain(mp)
    audio.abandonAudioFocusRequest(focus)
    onSpeaking(null)
  }

  private fun end(played: Boolean) {
    val callback = onEnd ?: return
    onEnd = null
    callback(played)
  }

  /** A player for `<base>.mp3`, prepared; null when the clip cannot be read. Blocks, so never on the main thread. */
  private fun prepare(base: String): MediaPlayer? {
    val mp = MediaPlayer() // created off the main thread, so its events (completion) come on the main looper
    return try {
      context.assets.openFd("$base.mp3").use { mp.setDataSource(it) }
      mp.setAudioAttributes(attributes)
      mp.prepare()
      mp
    } catch (e: IOException) {
      Log.w(TAG, "say $base: $e")
      mp.release()
      null
    }
  }

  private fun releaseOffMain(mp: MediaPlayer) {
    Thread(mp::release, "RinVoiceRelease").start() // not in [scope]: it may already be cancelled (the screen left)
  }

  /**
   * When media time 0 reached the speaker, from MediaPlayer's timestamp (it includes the output latency). Null when
   * no timestamp shows up within [TIMESTAMP_WAIT_MS], or when the line is cut off (the player released) meanwhile; the
   * caller then uses the start() time. Blocks on each call, so never on the main thread.
   */
  private suspend fun firstSampleAt(mp: MediaPlayer): Long? {
    repeat((TIMESTAMP_WAIT_MS / POLL_MS).toInt()) {
      val ts = try { mp.timestamp } catch (_: IllegalStateException) { return null }
      if (ts != null && ts.mediaClockRate > 0f && ts.anchorMediaTimeUs > 0) {
        val anchorEpochMs = System.currentTimeMillis() - (System.nanoTime() - ts.anchorSystemNanoTime) / 1_000_000
        return anchorEpochMs - (ts.anchorMediaTimeUs / 1000 / ts.mediaClockRate).toLong()
      }
      delay(POLL_MS)
    }
    return null
  }

  /** The clip's mouth track and where it came from, or null when the clip is missing. */
  private fun loadMouth(base: String): Pair<MouthTrack, String>? {
    val assets = context.assets
    runCatching { assets.open("$base.mouth.json").bufferedReader().use { it.readText() } }
      .getOrNull()
      ?.let(MouthTrack::parse)
      ?.let {
        return it to "track"
      }
    val pcm = runCatching { assets.openFd("$base.mp3").use(::decodeMono) }.getOrNull() ?: return null
    return MouthTrack.fromLoudness(pcm.first, pcm.second) to "loudness"
  }

  private companion object {
    const val TAG = "RinVoice"
    const val POLL_MS = 10L
    const val TIMESTAMP_WAIT_MS = 500L
    val SPEECH: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
  }
}

/** Decodes a short clip to mono 16-bit PCM with MediaCodec; returns the samples and their rate. */
internal fun decodeMono(fd: AssetFileDescriptor): Pair<ShortArray, Int>? {
  val extractor = MediaExtractor()
  var codec: MediaCodec? = null
  try {
    extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
    val track = (0 until extractor.trackCount).firstOrNull {
      extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: return null
    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)
    val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec = decoder
    decoder.configure(format, null, null, 0)
    decoder.start()
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var out = ShortArray(1 shl 16)
    var count = 0
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    while (true) {
      if (!inputDone) {
        val index = decoder.dequeueInputBuffer(10_000)
        if (index >= 0) {
          val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
          if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
          } else {
            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }
      val index = decoder.dequeueOutputBuffer(info, 10_000)
      if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        rate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = decoder.outputFormat.getIntegerOrDefault(AudioFormat.ENCODING_PCM_16BIT)
        if (encoding != AudioFormat.ENCODING_PCM_16BIT) return null
      } else if (index >= 0) {
        val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        while (buffer.remaining() >= channels) {
          var sum = 0
          repeat(channels) { sum += buffer.get() }
          if (count == out.size) out = out.copyOf(out.size * 2)
          out[count++] = (sum / channels).toShort()
        }
        decoder.releaseOutputBuffer(index, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
      }
    }
    return out.copyOf(count) to rate
  } catch (e: IOException) {
    Log.w("RinVoice", "decode: $e")
    return null
  } catch (e: IllegalStateException) {
    Log.w("RinVoice", "decode: $e") // MediaCodec.CodecException is one
    return null
  } finally {
    codec?.release()
    extractor.release()
  }
}

private fun MediaFormat.getIntegerOrDefault(default: Int): Int =
  if (containsKey(MediaFormat.KEY_PCM_ENCODING)) getInteger(MediaFormat.KEY_PCM_ENCODING) else default

package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What RingService plays: the alarm theme or the beep, driven by the same gain, pause and play. */
interface RingSound {
  fun setGain(gain: Float)

  fun play()

  fun pause()

  fun release()
}

/** One alarm theme (UX.7, D30): `music/<id>.mp3` in the APK, named for the editor. */
@Serializable data class MusicTheme(val id: String, val name: String)

/** The alarm themes this build carries, in Rin's rotation order. */
fun interface MusicCatalog {
  fun themes(): List<MusicTheme>
}

/**
 * Reads `music/themes.json`, keeping only themes whose file is in the APK. Like Rin's voice, the music never enters
 * the public repo (`rin.music` in local.properties): builds without it have no themes and every alarm beeps.
 */
class AssetMusicCatalog @Inject constructor(@ApplicationContext private val context: Context) : MusicCatalog {
  private val themes: List<MusicTheme> by lazy {
    runCatching {
        val files = context.assets.list(DIR).orEmpty().toSet()
        val listed = context.assets.open("$DIR/$INDEX").use { Json.decodeFromString<List<MusicTheme>>(it.reader().readText()) }
        listed.filter { "${it.id}.mp3" in files }
      }
      .getOrDefault(emptyList())
  }

  override fun themes(): List<MusicTheme> = themes

  companion object {
    const val DIR = "music"
    const val INDEX = "themes.json"

    fun path(id: String) = "$DIR/$id.mp3"
  }
}

/**
 * An alarm theme on USAGE_ALARM, looped without a gap. The file is decoded on a thread of its own and played as it
 * decodes, so the first notes come at once; the decoded sound is kept, and every later loop plays from memory. If the
 * file cannot be opened or decoded before any sound came out, the beep plays instead, in this same ring: an alarm
 * must always sound. Gain, pause and play work as on the beep, so the ramp, the game's quiet, ducking under Rin and
 * the call pause behave the same.
 */
class MusicPlayer(private val open: () -> AssetFileDescriptor) : RingSound {
  private val track: AudioTrack =
    AudioTrack.Builder()
      .setAudioAttributes(ALARM_AUDIO)
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(SAMPLE_RATE)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .build()
      )
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(
        maxOf(
          AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
          SAMPLE_RATE / 4 * CHANNELS * 2,
        )
      )
      .build()

  @Volatile private var running = true

  /** What loops, for the ring log: "music", "beep" (fell back), or null during the first pass. */
  @Volatile var outcome: String? = null
    private set

  private val writer = thread(name = "RinAlarm-music", isDaemon = true) { run() }

  override fun setGain(gain: Float) {
    track.setVolume(gain)
  }

  override fun play() = track.play()

  override fun pause() = track.pause()

  override fun release() {
    running = false
    runCatching { track.pause() }
    runCatching { track.flush() }
    // A write blocked on a paused track returns once the track is released.
    track.release()
    writer.join(RELEASE_WAIT_MS)
  }

  private fun run() {
    val pcm = PcmBuffer(MAX_SECONDS * SAMPLE_RATE * CHANNELS)
    var padding = 0
    runCatching { padding = decodeWhilePlaying(pcm) }.onFailure { Log.w(TAG, "theme stopped decoding", it) }
    if (!running) return
    // What decoded before a failure still loops, if there is at least a second of it; otherwise the beep.
    val loop = pcm.trimmed(padding)
    if (loop.size < SAMPLE_RATE * CHANNELS) {
      outcome = "beep"
      loopForever(stereoBeep())
    } else {
      outcome = "music"
      loopForever(loop)
    }
  }

  /**
   * Plays the file once as it decodes, keeping it in [pcm], and returns the samples of encoder padding to drop from
   * the loop's end. Skips the encoder's delay at the start; the first pass plays the padding (a few hundredths of a
   * second of silence, once). Stops at the end of the file or when [pcm] is full.
   */
  private fun decodeWhilePlaying(pcm: PcmBuffer): Int {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      open().use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
      val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).mime()?.startsWith("audio/") == true }
      require(index != null) { "no audio track" }
      extractor.selectTrack(index)
      val format = extractor.getTrackFormat(index)
      val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      require(rate == SAMPLE_RATE && channels in 1..2) { "unsupported format: $rate Hz, $channels ch" }
      var skip = format.intOr(KEY_DELAY, 0)
      val padding = format.intOr(KEY_PADDING, 0)

      val decoder = MediaCodec.createDecoderByType(checkNotNull(format.mime()))
      codec = decoder
      decoder.configure(format, null, null, 0)
      decoder.start()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      while (running) {
        if (!inputDone) {
          val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
          if (inIndex >= 0) {
            val buffer = checkNotNull(decoder.getInputBuffer(inIndex))
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
              decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          // The decoder's own count wins over the file's (a decoder may hand mono out as stereo).
          channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          require(channels in 1..2) { "unsupported decoder output: $channels ch" }
        }
        if (outIndex < 0) continue
        val out = checkNotNull(decoder.getOutputBuffer(outIndex)).order(ByteOrder.nativeOrder()).asShortBuffer()
        val samples = ShortArray(info.size / 2)
        out.position(info.offset / 2)
        out.get(samples, 0, minOf(samples.size, out.remaining()))
        decoder.releaseOutputBuffer(outIndex, false)
        var stereo = if (channels == 1) Pcm.monoToStereo(samples) else samples
        if (skip > 0) {
          val drop = minOf(skip, stereo.size / CHANNELS)
          stereo = stereo.copyOfRange(drop * CHANNELS, stereo.size)
          skip -= drop
        }
        if (stereo.isNotEmpty()) {
          if (!pcm.append(stereo)) break
          if (write(stereo) < 0) break
        }
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
      }
      return padding * CHANNELS
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      extractor.release()
    }
  }

  private fun loopForever(loop: ShortArray) {
    var at = 0
    while (running) {
      val n = minOf(CHUNK, loop.size - at)
      if (write(loop, at, n) < 0) return
      at = (at + n) % loop.size
    }
  }

  private fun write(data: ShortArray, from: Int = 0, count: Int = data.size): Int =
    if (!running) -1 else runCatching { track.write(data, from, count, AudioTrack.WRITE_BLOCKING) }.getOrDefault(-1)

  private fun MediaFormat.mime(): String? = getString(MediaFormat.KEY_MIME)

  private fun MediaFormat.intOr(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback

  companion object {
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2

    /** Longer files are cut here: the loop stays in memory (2 min of stereo is about 21 MB). */
    const val MAX_SECONDS = 120

    private const val TAG = "RinMusic"
    private const val TIMEOUT_US = 10_000L
    private const val CHUNK = 4_096
    private const val RELEASE_WAIT_MS = 500L

    // MediaFormat.KEY_ENCODER_DELAY/PADDING are API 30; the strings are the same on 29.
    private const val KEY_DELAY = "encoder-delay"
    private const val KEY_PADDING = "encoder-padding"

    private fun stereoBeep(): ShortArray = Pcm.monoToStereo(TonePlayer.tonePattern())
  }
}

/** Sample helpers, kept pure for tests. */
internal object Pcm {
  fun monoToStereo(mono: ShortArray): ShortArray {
    val out = ShortArray(mono.size * 2)
    for (i in mono.indices) {
      out[2 * i] = mono[i]
      out[2 * i + 1] = mono[i]
    }
    return out
  }
}

/** The decoded theme, grown chunk by chunk up to [capacity] samples. */
internal class PcmBuffer(private val capacity: Int) {
  private var data = ShortArray(minOf(capacity, INITIAL))
  var size = 0
    private set

  /** Appends [chunk]; false once the buffer is full, and nothing more is kept (or played). */
  fun append(chunk: ShortArray): Boolean {
    if (size + chunk.size > capacity) return false
    if (size + chunk.size > data.size) data = data.copyOf(minOf(capacity, maxOf(data.size * 2, size + chunk.size)))
    chunk.copyInto(data, size)
    size += chunk.size
    return true
  }

  /** Everything kept, less [dropEnd] samples of padding at the end. */
  fun trimmed(dropEnd: Int): ShortArray = data.copyOf(maxOf(0, size - dropEnd.coerceAtLeast(0)))

  private companion object {
    const val INITIAL = 1 shl 20
  }
}

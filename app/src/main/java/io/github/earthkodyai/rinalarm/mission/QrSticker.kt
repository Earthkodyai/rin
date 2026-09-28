package io.github.earthkodyai.rinalarm.mission

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.math.hypot

/**
 * The code the QR mission asks for (task 3.2; user decision: the app's own sticker or a code already in the home).
 * Only a salted SHA-256 of the code's content is kept, never the content: a code in the home can carry a secret (a
 * Wi-Fi QR holds the password), and this record lives in device-protected storage, readable whenever the phone is on.
 *
 * @property payload the generated sticker's content, kept only for [Source.GENERATED] so it can be shared again.
 * @property closeFraction the size (StickerCheck.fraction) it had when registered next to it.
 * @property bedFraction the largest size it was seen at from bed during the bed check, or null when never seen.
 */
data class QrSticker(
  val salt: String,
  val hash: String,
  val source: Source,
  val format: CodeFormat,
  val payload: String?,
  val closeFraction: Float,
  val bedFraction: Float?,
  val setAt: Instant,
) {
  enum class Source(val stored: String) {
    GENERATED("generated"),
    EXISTING("existing");

    companion object {
      fun fromStored(value: String?): Source? = entries.firstOrNull { it.stored == value }
    }
  }

  fun matches(content: String): Boolean = StickerKey.hash(salt, content) == hash
}

/** QR codes, and everything else ML Kit reads (product barcodes, Data Matrix...), shown to the user by kind only. */
enum class CodeFormat(val stored: String) {
  QR("qr"),
  BARCODE("barcode"),
  OTHER_2D("other_2d");

  companion object {
    fun fromStored(value: String?): CodeFormat = entries.firstOrNull { it.stored == value } ?: OTHER_2D
  }
}

/**
 * One code in one camera frame. [fraction] is its size: the longest side of its outline over the frame's shorter
 * side, so it does not depend on how the phone or the code is turned.
 */
data class SeenCode(val content: String, val format: CodeFormat, val fraction: Float)

enum class ScanVerdict {
  /** The sticker, close enough: the mission passes. */
  MATCH,
  /** The sticker, but smaller than [QrScanPolicy.MIN_FRACTION]: "come closer". */
  TOO_FAR,
  /** Only other codes. */
  OTHER,
  NONE,
}

object StickerKey {
  private val random = SecureRandom()

  fun newSalt(): String = hex(ByteArray(16).also(random::nextBytes))

  fun hash(salt: String, content: String): String =
    hex(MessageDigest.getInstance("SHA-256").digest((salt + content).toByteArray(Charsets.UTF_8)))

  /**
   * A new sticker's content: "RINALARM:" and 16 base32 characters (80 random bits). Only QR alphanumeric-mode
   * characters, so it fits a version-2 QR whose large modules read well in a dim room.
   */
  fun newPayload(): String {
    val bytes = ByteArray(10).also(random::nextBytes)
    val bits = bytes.fold(StringBuilder()) { acc, b -> acc.append(Integer.toBinaryString(b.toInt() and 0xff).padStart(8, '0')) }
    return PAYLOAD_PREFIX + bits.chunked(5).joinToString("") { BASE32[it.toInt(2)].toString() }
  }

  const val PAYLOAD_PREFIX = "RINALARM:"
  private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

  private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

/** The numbers that shape a QR scan, free of Android types so tests can pin them. */
object QrScanPolicy {
  /**
   * The sticker counts only at this size or more (user decision: 1x zoom plus a size bar). It is what keeps a scan
   * from bed out; the value is tuned on dev trials and frozen before the held-out run (docs/spikes/3.2-qr.md).
   */
  const val MIN_FRACTION = 0.20f

  /** The camera closes this long after it opened, or after the last sighting or step (user decision: tap to open). */
  val CAMERA_IDLE: Duration = Duration.ofSeconds(60)

  /** How long setup aims from bed; the sticker must never pass in that time. */
  val BED_CHECK: Duration = Duration.ofSeconds(10)

  fun judge(codes: List<SeenCode>, isSticker: (SeenCode) -> Boolean): ScanVerdict {
    if (codes.isEmpty()) return ScanVerdict.NONE
    val sticker = codes.filter(isSticker).maxByOrNull { it.fraction } ?: return ScanVerdict.OTHER
    return if (sticker.fraction >= MIN_FRACTION) ScanVerdict.MATCH else ScanVerdict.TOO_FAR
  }

  /** [SeenCode.fraction] from a code's corner points (x0, y0, x1, y1...) in a [width] x [height] frame. */
  fun fraction(corners: FloatArray, width: Int, height: Int): Float {
    val short = minOf(width, height)
    if (short <= 0 || corners.size < 4) return 0f
    val n = corners.size / 2
    var longest = 0.0
    for (i in 0 until n) {
      val j = (i + 1) % n
      longest = maxOf(longest, hypot((corners[2 * j] - corners[2 * i]).toDouble(), (corners[2 * j + 1] - corners[2 * i + 1]).toDouble()))
    }
    return (longest / short).toFloat()
  }
}

/**
 * The flashlight (user decision: on by itself in a dark room, with a switch). It comes on after [DARK_FRAMES] frames
 * in a row darker than [DARK_LUMA], and never goes off by itself: once lit, the frame looks bright because of it. After
 * the user uses the switch, only the switch decides.
 */
class TorchControl {
  var on = false
    private set

  var auto = false
    private set

  private var manual = false
  private var darkFrames = 0

  /** Mean luma (0-255) of a frame; true when the torch should now turn on. */
  fun onFrame(luma: Int): Boolean {
    if (manual || on) return false
    darkFrames = if (luma < DARK_LUMA) darkFrames + 1 else 0
    if (darkFrames < DARK_FRAMES) return false
    on = true
    auto = true
    return true
  }

  fun toggle(): Boolean {
    manual = true
    auto = false
    on = !on
    return on
  }

  companion object {
    const val DARK_LUMA = 50
    const val DARK_FRAMES = 5
  }
}

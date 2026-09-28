package io.github.earthkodyai.rinalarm.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.mission.CodeFormat
import io.github.earthkodyai.rinalarm.mission.QrSticker
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The one registered QR sticker (task 3.2), or null before setup. */
interface StickerStore {
  /** Synchronous: RingService plans the mission on the main thread before posting its notification. */
  fun current(): QrSticker?

  /** Replaces the sticker, and forgets [pendingPayload]. */
  suspend fun save(sticker: QrSticker)

  /**
   * A generated sticker that setup has shown but not finished. Kept so leaving setup halfway (Back, a slip) and coming
   * back offers the same code again, and a sticker already printed stays usable.
   */
  fun pendingPayload(): String?

  suspend fun setPendingPayload(payload: String)
}

/**
 * [StickerStore] in device-protected SharedPreferences, so a ring before the first unlock still knows whether a sticker
 * exists. SharedPreferences rather than the app's DataStore: they are read once, then served from memory
 * synchronously, which the readiness check needs. Only the salted hash is stored (see [QrSticker]).
 */
@Singleton
class SharedPrefsStickerStore @Inject constructor(@ApplicationContext context: Context) : StickerStore {
  private val prefs = StorageFiles.deviceProtected(context).getSharedPreferences(NAME, Context.MODE_PRIVATE)

  override fun current(): QrSticker? {
    val salt = prefs.getString(SALT, null) ?: return null
    val hash = prefs.getString(HASH, null) ?: return null
    val source = QrSticker.Source.fromStored(prefs.getString(SOURCE, null)) ?: return null
    return QrSticker(
      salt = salt,
      hash = hash,
      source = source,
      format = CodeFormat.fromStored(prefs.getString(FORMAT, null)),
      payload = prefs.getString(PAYLOAD, null),
      closeFraction = prefs.getFloat(CLOSE, 0f),
      bedFraction = prefs.getFloat(BED, -1f).takeIf { it >= 0f },
      setAt = Instant.ofEpochMilli(prefs.getLong(SET_AT, 0)),
    )
  }

  override suspend fun save(sticker: QrSticker) =
    withContext(Dispatchers.IO) {
      prefs.edit(commit = true) {
        clear()
        putString(SALT, sticker.salt)
        putString(HASH, sticker.hash)
        putString(SOURCE, sticker.source.stored)
        putString(FORMAT, sticker.format.stored)
        sticker.payload?.let { putString(PAYLOAD, it) }
        putFloat(CLOSE, sticker.closeFraction)
        putFloat(BED, sticker.bedFraction ?: -1f)
        putLong(SET_AT, sticker.setAt.toEpochMilli())
      }
    }

  override fun pendingPayload(): String? = prefs.getString(PENDING, null)

  override suspend fun setPendingPayload(payload: String) =
    withContext(Dispatchers.IO) { prefs.edit(commit = true) { putString(PENDING, payload) } }

  private companion object {
    const val PENDING = "pending_payload"
    const val NAME = "qr_sticker"
    const val SALT = "salt"
    const val HASH = "hash"
    const val SOURCE = "source"
    const val FORMAT = "format"
    const val PAYLOAD = "payload"
    const val CLOSE = "close_fraction"
    const val BED = "bed_fraction"
    const val SET_AT = "set_at"
  }
}

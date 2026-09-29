package io.github.earthkodyai.rinalarm.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.earthkodyai.rinalarm.data.StickerStore
import javax.inject.Inject

/** Which missions can run on this phone right now. Read fresh each time: permissions change in Settings. */
fun interface MissionReadiness {
  fun check(): Map<MissionType, Readiness>
}

class AndroidMissionReadiness
@Inject
constructor(@ApplicationContext private val context: Context, private val stickers: StickerStore) : MissionReadiness {
  override fun check(): Map<MissionType, Readiness> = MissionType.offeredEntries.associateWith(::readiness)

  private fun readiness(type: MissionType): Readiness =
    when (type) {
      // A touch screen and a speaker: every phone.
      MissionType.PADS -> Readiness.READY
      MissionType.QR ->
        when {
          !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> Readiness.NO_SENSOR
          ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
            Readiness.NO_PERMISSION
          stickers.current() == null -> Readiness.NOT_SET_UP
          // ML Kit starts from a content provider that is not direct-boot aware, so before the first unlock after a
          // reboot the scanner may not exist: Rin picks another mission rather than a camera that cannot read.
          context.getSystemService(UserManager::class.java)?.isUserUnlocked == false -> Readiness.BEFORE_UNLOCK
          else -> Readiness.READY
        }
    }

  companion object {
    /**
     * The runtime permission a mission asks for when the user picks it (D15: at setup, never at install), or null
     * when it needs none. Walking needs none since it stopped using Android's step sensors (3.1 measurement).
     */
    fun permissionFor(type: MissionType): String? =
      when (type) {
        MissionType.PADS -> null
        MissionType.QR -> Manifest.permission.CAMERA
      }
  }
}

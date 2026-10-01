package io.github.earthkodyai.rinalarm.mission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Which missions can run on this phone right now. Read fresh each time: permissions change in Settings. */
fun interface MissionReadiness {
  fun check(): Map<MissionType, Readiness>
}

class AndroidMissionReadiness
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val vosk: VoskModels,
) : MissionReadiness {
  override fun check(): Map<MissionType, Readiness> = MissionType.entries.associateWith(::readiness)

  private fun readiness(type: MissionType): Readiness =
    when (type) {
      // A touch screen and a speaker (pads), a screen (cups, drawn natively when her page is missing): every phone.
      MissionType.PADS,
      MissionType.CUPS -> Readiness.READY
      // Repeat after Rin (3.5): the mic, and the Vosk model in this build (every build fetches it).
      MissionType.SPEECH ->
        when {
          !context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) || !vosk.bundled() -> Readiness.NO_SENSOR
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
            Readiness.NO_PERMISSION
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
        MissionType.PADS,
        MissionType.CUPS -> null
        MissionType.SPEECH -> Manifest.permission.RECORD_AUDIO
      }
  }
}

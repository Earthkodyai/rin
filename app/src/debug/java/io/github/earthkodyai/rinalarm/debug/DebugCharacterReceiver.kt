package io.github.earthkodyai.rinalarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.earthkodyai.rinalarm.character.CharacterDebug
import io.github.earthkodyai.rinalarm.character.Mood

/**
 * Debug builds only. Crashes the character renderer, to check the still-image fallback and that the app survives, or
 * forces a mood, to see it and time it (logcat tag RinChar: `emotion <mood> toPage=.. total=..`):
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd crash
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd mood --es mood pouty
 *   [--ef intensity 0.5]
 */
class DebugCharacterReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.getStringExtra("cmd")) {
      "crash" -> Log.i(TAG, "crash requested, delivered=${CharacterDebug.crashRenderer.tryEmit(Unit)}")
      "mood" -> {
        val mood = Mood.fromWire(intent.getStringExtra("mood").orEmpty())
        if (mood == null) {
          Log.w(TAG, "unknown mood; one of ${Mood.entries.joinToString { it.wire }}")
          return
        }
        val intensity = intent.getFloatExtra("intensity", 1f)
        Log.i(TAG, "mood $mood $intensity, delivered=${CharacterDebug.mood.tryEmit(mood to intensity)}")
      }
      else -> Log.w(TAG, "unknown cmd")
    }
  }

  private companion object {
    const val TAG = "RinDebug"
  }
}

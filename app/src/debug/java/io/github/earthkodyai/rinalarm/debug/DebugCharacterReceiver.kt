package io.github.earthkodyai.rinalarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.earthkodyai.rinalarm.character.CharacterDebug
import io.github.earthkodyai.rinalarm.character.Gesture
import io.github.earthkodyai.rinalarm.character.Mood

/**
 * Debug builds only. Crashes the character renderer, to check the still-image fallback and that the app survives,
 * forces a mood, to see it and time it (logcat tag RinChar: `emotion <mood> toPage=.. total=..`), plays a gesture, or
 * says a debug voice line from assets voice/dev/ (logcat RinVoice: when its first sample played):
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd crash
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd mood --es mood pouty
 *   [--ef intensity 0.5]
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd gesture --es name wave
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd say --es clip L01
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
      "gesture" -> {
        val gesture = Gesture.fromWire(intent.getStringExtra("name").orEmpty())
        if (gesture == null) {
          Log.w(TAG, "unknown gesture; one of ${Gesture.entries.joinToString { it.wire }}")
          return
        }
        Log.i(TAG, "gesture $gesture, delivered=${CharacterDebug.gesture.tryEmit(gesture)}")
      }
      "say" -> {
        val clip = intent.getStringExtra("clip").orEmpty()
        if (!Regex("[A-Za-z0-9_-]+").matches(clip)) {
          Log.w(TAG, "say needs --es clip <name> (a file in assets voice/dev/)")
          return
        }
        Log.i(TAG, "say $clip, delivered=${CharacterDebug.say.tryEmit(clip)}")
      }
      else -> Log.w(TAG, "unknown cmd")
    }
  }

  private companion object {
    const val TAG = "RinDebug"
  }
}

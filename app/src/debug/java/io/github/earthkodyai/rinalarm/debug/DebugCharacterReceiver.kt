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
 * forces a mood, to see it and time it (logcat tag RinChar: `emotion <mood> toPage=.. total=..`), plays a gesture,
 * says a debug voice line from assets voice/dev/ (logcat RinVoice: when its first sample played), measures frame
 * pacing over `sec` seconds of rendering (logcat RinChar: `stats Stats(...)`), or changes the fps cap until the page
 * reloads (tools/character/phase-exit.sh uses the last two):
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd crash
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd mood --es mood pouty
 *   [--ef intensity 0.5]
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd gesture --es name wave
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd say --es clip L01
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd stats --ei sec 30
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd fps --ei cap 120
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
      "stats" -> {
        val sec = intent.getIntExtra("sec", 30).coerceIn(1, 300)
        Log.i(TAG, "stats ${sec}s, delivered=${CharacterDebug.measureFrames.tryEmit(sec * 1000L)}")
      }
      "fps" -> {
        val cap = intent.getIntExtra("cap", 0)
        if (cap !in 1..240) {
          Log.w(TAG, "fps needs --ei cap 1..240")
          return
        }
        Log.i(TAG, "fps cap $cap, delivered=${CharacterDebug.fpsCap.tryEmit(cap)}")
      }
      else -> Log.w(TAG, "unknown cmd")
    }
  }

  private companion object {
    const val TAG = "RinDebug"
  }
}

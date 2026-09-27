package io.github.earthkodyai.rinalarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.earthkodyai.rinalarm.character.CharacterDebug

/**
 * Debug builds only. Crashes the character renderer, to check the still-image fallback and that the app survives:
 *
 * adb shell am broadcast -n io.github.earthkodyai.rinalarm/.debug.DebugCharacterReceiver --es cmd crash
 */
class DebugCharacterReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.getStringExtra("cmd")) {
      "crash" -> Log.i(TAG, "crash requested, delivered=${CharacterDebug.crashRenderer.tryEmit(Unit)}")
      else -> Log.w(TAG, "unknown cmd")
    }
  }

  private companion object {
    const val TAG = "RinDebug"
  }
}

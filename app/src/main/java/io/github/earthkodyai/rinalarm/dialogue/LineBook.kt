package io.github.earthkodyai.rinalarm.dialogue

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Rin's lines for the screens, picked by pool ([LinePicker]). */
fun interface LineBook {
  /** A line from [pool], or null when there is none (or the script could not be read). Main thread only. */
  suspend fun pick(pool: String, day: LocalDate, morning: Any): Line?
}

/**
 * The script from assets, read once on first use. One picker for the whole app, so a morning's session bags and the
 * events' last lines carry from one ring screen to the next (a snooze and its re-ring) while the process lives.
 */
@Singleton
class AssetLineBook @Inject constructor(@ApplicationContext private val context: Context) : LineBook {
  private val lock = Mutex()
  private var picker: LinePicker? = null
  private var failed = false

  override suspend fun pick(pool: String, day: LocalDate, morning: Any): Line? =
    picker()?.pick(pool, day, morning)

  private suspend fun picker(): LinePicker? =
    lock.withLock {
      picker
        ?: if (failed) null
        else
          runCatching {
              withContext(Dispatchers.IO) {
                context.assets.open(Script.ASSET).use { Script.parse(it.reader().readText()) }
              }
            }
            .onFailure {
              // Rin just stays quiet: her lines are never needed to stop an alarm.
              Log.w(TAG, "script: $it")
              failed = true
            }
            .getOrNull()
            ?.let { LinePicker(it).also { p -> picker = p } }
    }

  private companion object {
    const val TAG = "RinLines"
  }
}

/** Moments elsewhere in the app that Rin answers on the home screen. */
@Singleton
class HomeMoments @Inject constructor() {
  /** An alarm was saved (the editor closes and she says so on the home screen). */
  val alarmSaved = Channel<Unit>(Channel.CONFLATED)
}

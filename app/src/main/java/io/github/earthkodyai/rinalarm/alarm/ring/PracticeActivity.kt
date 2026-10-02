package io.github.earthkodyai.rinalarm.alarm.ring

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.earthkodyai.rinalarm.mission.Difficulty
import io.github.earthkodyai.rinalarm.mission.MissionType
import io.github.earthkodyai.rinalarm.theme.RinThemedContent
import io.github.earthkodyai.rinalarm.theme.ThemeClock
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * A practice round of one game (UX.8): the ring screen with no ring behind it ([RingViewModel.EXTRA_PRACTICE]). Its
 * own activity in the app's task, not RingActivity, which is a single instance a real ring must always get. A real
 * ring ends the round (its screen opens over this one), and so does leaving the screen: a round left running would
 * time out and scold nobody.
 */
@AndroidEntryPoint
class PracticeActivity : ComponentActivity() {
  private val viewModel: RingViewModel by viewModels()
  @Inject lateinit var themeClock: ThemeClock
  @Inject lateinit var ringState: RingState

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.CREATED) { ringState.active.collect { if (it != null) finish() } }
    }
    // Nothing to send anywhere: a practice round has no ring to snooze or dismiss.
    setContent { RinThemedContent(themeClock) { RingRoute(viewModel, onFinish = ::finish) {} } }
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations) finish()
  }

  companion object {
    /** A round of [game]; [level] and [scold] from the editor's draft, or (null) the ones a new alarm gets. */
    fun intent(context: Context, game: MissionType, level: Difficulty? = null, scold: Boolean? = null): Intent =
      Intent(context, PracticeActivity::class.java).putExtra(RingViewModel.EXTRA_PRACTICE, game.stored).apply {
        level?.let { putExtra(RingViewModel.EXTRA_LEVEL, it.stored) }
        scold?.let { putExtra(RingViewModel.EXTRA_SCOLD, it) }
      }
  }
}

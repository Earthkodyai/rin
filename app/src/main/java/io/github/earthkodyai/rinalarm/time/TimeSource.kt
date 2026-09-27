package io.github.earthkodyai.rinalarm.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time and zone. Injected so tests can pin both.
 *
 * [zone] is read on every call rather than cached: the user can change timezone while the process is alive, and
 * Android updates the default zone when that happens.
 */
interface TimeSource {
  fun now(): Instant

  fun zone(): ZoneId

  /** Emits once straight away, then at every wall-clock minute boundary. */
  val minuteTicks: Flow<Unit>
}

class SystemTimeSource @Inject constructor() : TimeSource {
  override fun now(): Instant = Instant.now()

  override fun zone(): ZoneId = ZoneId.systemDefault()

  override val minuteTicks: Flow<Unit> = flow {
    while (true) {
      emit(Unit)
      val millisIntoMinute = now().toEpochMilli() % Duration.ofMinutes(1).toMillis()
      delay(Duration.ofMinutes(1).toMillis() - millisIntoMinute)
    }
  }
}

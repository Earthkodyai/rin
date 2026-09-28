package io.github.earthkodyai.rinalarm.character

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What the character page (web/character/src/bridge.ts, protocol 1) tells the app. Unknown types and malformed JSON
 * parse to null and are ignored, so the page and the app can each add message types first.
 */
sealed interface CharacterMessage {
  @Serializable
  data class Ready(val ms: LoadTimings, val model: ModelInfo, val pixelRatio: Double, val fpsCap: Int) :
    CharacterMessage

  @Serializable data class Error(val message: String) : CharacterMessage

  /**
   * A mood change is fully shown. [EmotionTimings.total] runs from the app's send to the blend's last frame; the
   * phase-exit bar is 300 ms (plan phase 2).
   */
  @Serializable data class EmotionShown(val mood: String, val ms: EmotionTimings) : CharacterMessage

  @Serializable data class EmotionTimings(val toPage: Long, val total: Long)

  /** The user tapped her head (the only part that reacts); the page has already reacted. */
  @Serializable data class Tap(val part: String) : CharacterMessage

  /** The page started a gesture ([ok]), or could not because its file failed to load. */
  @Serializable data class GestureStarted(val name: String, val ok: Boolean) : CharacterMessage

  /**
   * Frame pacing over a window the app asked for ([CharacterCommand.MeasureFrames]); paused time is left out. The
   * phase-exit bars (plan phase 2, task 2.5): at the 30 fps cap, [avgFps] ≥ 29 and no [over50]; with the cap lifted
   * to 120, [avgFps] ≥ 45 (S2's bar, the headroom a slower phone would need).
   */
  @Serializable
  data class Stats(
    val frames: Int,
    val seconds: Double,
    val avgFps: Double,
    val p1LowFps: Double,
    val over50: Int,
    val maxMs: Double,
    val fpsCap: Double,
    val hitches: List<Hitch> = emptyList(),
  ) : CharacterMessage

  /** A frame interval over 50 ms, ending [at] ms into the window. */
  @Serializable data class Hitch(val at: Long, val ms: Double)

  @Serializable
  data class LoadTimings(
    val pageToFirstFrame: Long,
    val fetch: Long,
    val parse: Long,
    val compile: Long,
    val nativeToFirstFrame: Long? = null,
  )

  @Serializable
  data class ModelInfo(
    val bytes: Long,
    val meshes: Int,
    val triangles: Int,
    val textures: Int,
    val expressions: List<String>,
  )

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CharacterMessage? =
      try {
        val obj: JsonObject = json.parseToJsonElement(text).jsonObject
        when (obj["type"]?.jsonPrimitive?.content) {
          "ready" -> json.decodeFromJsonElement<Ready>(obj)
          "error" -> json.decodeFromJsonElement<Error>(obj)
          "emotion" -> json.decodeFromJsonElement<EmotionShown>(obj)
          "tap" -> json.decodeFromJsonElement<Tap>(obj)
          "gesture" -> json.decodeFromJsonElement<GestureStarted>(obj)
          "stats" -> json.decodeFromJsonElement<Stats>(obj)
          else -> null
        }
      } catch (_: SerializationException) {
        null
      } catch (_: IllegalArgumentException) {
        null // not an object, or a field of the wrong kind
      }
  }
}

/** What the app tells the page. */
sealed interface CharacterCommand {
  val json: String

  data object Pause : CharacterCommand {
    override val json = """{"type":"pause"}"""
  }

  data object Resume : CharacterCommand {
    override val json = """{"type":"resume"}"""
  }

  data object Hush : CharacterCommand {
    override val json = """{"type":"hush"}"""
  }

  data class PlayGesture(val gesture: Gesture) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "gesture")
          put("name", gesture.wire)
        }
        .toString()
  }

  /** Move the mouth along [mouth] from [at], the epoch ms the audio's first sample played (the page shares the clock). */
  data class Speak(val mouth: MouthTrack, val at: Long) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "speak")
          put("mouth", buildJsonObject {
            put("v", mouth.v)
            put("fps", mouth.fps)
            put("f", mouth.f)
          })
          put("at", at)
        }
        .toString()
  }

  /** Measure frame pacing over the next [ms] of rendering; the page answers with [CharacterMessage.Stats]. */
  data class MeasureFrames(val ms: Long) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "stats")
          put("ms", ms)
        }
        .toString()
  }

  /** Change the page's frame-rate cap (debug: measuring the headroom above [CharacterView]'s 30). */
  data class FpsCap(val cap: Int) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "fps")
          put("cap", cap)
        }
        .toString()
  }

  /** Blend to [mood]; [at] (epoch ms, the page shares the clock) lets the page time the change end to end. */
  data class Emotion(val mood: Mood, val intensity: Float, val at: Long) : CharacterCommand {
    override val json: String
      get() =
        buildJsonObject {
            put("type", "emotion")
            put("mood", mood.wire)
            put("intensity", intensity.coerceIn(0f, 1f))
            put("at", at)
          }
          .toString()
  }
}

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

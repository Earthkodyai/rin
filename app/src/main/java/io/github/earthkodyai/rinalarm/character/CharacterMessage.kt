package io.github.earthkodyai.rinalarm.character

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the character page (web/character/src/bridge.ts, protocol 1) tells the app. Unknown types and malformed JSON
 * parse to null and are ignored, so the page and the app can each add message types first.
 */
sealed interface CharacterMessage {
  @Serializable
  data class Ready(val ms: LoadTimings, val model: ModelInfo, val pixelRatio: Double, val fpsCap: Int) :
    CharacterMessage

  @Serializable data class Error(val message: String) : CharacterMessage

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
enum class CharacterCommand(val json: String) {
  PAUSE("""{"type":"pause"}"""),
  RESUME("""{"type":"resume"}"""),
}

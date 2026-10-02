package io.github.earthkodyai.rinalarm.character

import io.github.earthkodyai.rinalarm.mission.CupsAct
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

  /**
   * The cup table (task 3.4) is fully in view ([shown]), with each slot's cup at [x] across the view (0..1, for the tap
   * zones), or fully away. [base]: how far down the view the front of the cups' bases is (0..1; G.5), null from an
   * older page.
   */
  @Serializable data class CupsShown(val shown: Boolean, val x: List<Double> = emptyList(), val base: Double? = null) : CharacterMessage

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
          "cups" -> json.decodeFromJsonElement<CupsShown>(obj)
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

  /**
   * The shares of her view's height the screen covers at its top and bottom (UX.4, the ring screen's time and sheet):
   * the page frames her, and the cup table, into the open part between them, gliding when they change.
   */
  data class Insets(val top: Float, val bottom: Float) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "insets")
          put("top", top)
          put("bottom", bottom)
        }
        .toString()
  }

  /** Blend to [mood]; [at] (epoch ms, the page shares the clock) lets the page time the change end to end. */
  /**
   * The cup shuffle's act for the page to play (web/character/src/cups.ts), or null to put the table away. The act's
   * times are the elapsed clock's; [epochOffset] (wall clock minus elapsed clock, now) turns them into the page's.
   */
  data class Cups(val act: CupsAct?, val epochOffset: Long) : CharacterCommand {
    override val json: String
      get() = buildJsonObject {
          put("type", "cups")
          val act = act
          if (act == null) {
            put("act", JsonNull)
            return@buildJsonObject
          }
          put(
            "act",
            buildJsonObject {
              put("ball", act.ball)
              put("at", act.at + epochOffset)
              put("cups", act.cups)
              when (act) {
                is CupsAct.Rest -> put("kind", "rest")
                is CupsAct.Lift -> {
                  put("kind", "lift")
                  putJsonArray("lift") { act.lift.forEach(::add) }
                  putJsonArray("hands") { act.hands.forEach(::add) }
                  put("leadMs", act.leadMs)
                  put("upMs", act.upMs)
                  put("holdMs", act.holdMs)
                  put("downMs", act.downMs)
                  put("exitMs", act.exitMs)
                }
                is CupsAct.Shuffle -> {
                  put("kind", "shuffle")
                  putJsonArray("swaps") {
                    act.swaps.forEach { (p, q) ->
                      addJsonArray {
                        add(p)
                        add(q)
                      }
                    }
                  }
                  put("leadMs", act.leadMs)
                  put("swapMs", act.swapMs)
                  put("gapMs", act.gapMs)
                  put("exitMs", act.exitMs)
                }
              }
            },
          )
        }
        .toString()
  }

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

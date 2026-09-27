package dev.rinalarm.spike.s5

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One analysed camera frame. No pixels are kept: labels, timing, brightness and (part B) the two
 * image embeddings as base64 float16. Embeddings stay on the phone and in a git-ignored folder.
 */
data class Frame(
    val tMs: Long, val inferMs: Long, val embedMs: Long, val luma: Int, val lux: Float,
    val labels: List<Pair<String, Float>>, val embSmall: String?, val embLarge: String?,
)

/** A timed recording: a trial, a teach scan of one object, or the from-bed scan. */
class TrialRecorder(
    val type: String,
    val id: String,
    val session: String,
    val scene: Scene,
    val n: Int,
    val condition: Condition,
    val windowMs: Long,
    val startWallMs: Long,
    val startElapsedMs: Long,
    val luxStart: Float,
) {
    private val frames = ArrayList<Frame>()
    var width = 0
    var height = 0
    var rotation = 0

    @Synchronized fun add(frameElapsedMs: Long, f: (Long) -> Frame) {
        // A frame grabbed before Start was pressed belongs to no recording.
        if (frameElapsedMs < startElapsedMs) return
        frames += f(frameElapsedMs - startElapsedMs)
    }

    @Synchronized fun frameCount() = frames.size

    @Synchronized fun toJson(luxEnd: Float): JSONObject = JSONObject()
        .put("type", type).put("id", id).put("session", session)
        .put("kind", if (scene.negative) "negative" else "target")
        .put("scene", scene.id).put("name", scene.name).put("n", n)
        .put("light", condition.light).put("how", condition.how)
        .put("start", startWallMs).put("window_ms", windowMs)
        .put("lux_start", luxStart.toDouble()).put("lux_end", luxEnd.toDouble())
        .put("w", width).put("h", height).put("rot", rotation)
        .put("frames", JSONArray().apply {
            frames.forEach { f ->
                put(JSONObject().put("t", f.tMs).put("inf", f.inferMs).put("emb_ms", f.embedMs).put("luma", f.luma).put("lux", f.lux.toDouble())
                    .put("labels", JSONArray().apply {
                        f.labels.forEach { (text, conf) -> put(JSONArray().put(text).put(Math.round(conf * 1000) / 1000.0)) }
                    })
                    .apply { f.embSmall?.let { put("es", it) }; f.embLarge?.let { put("el", it) } })
            }
        })
}

/** Append-only JSONL log. Discards are separate records, so nothing is ever rewritten. */
class TrialStore(dir: File) {
    val file = File(dir.apply { mkdirs() }, "trials.jsonl")
    private val counts = HashMap<String, Int>()
    private val kept = HashMap<String, String>() // record id -> count key

    init {
        if (file.exists()) file.forEachLine { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
            when (o.optString("type")) {
                "trial", "teach", "bedscan" -> track(o)
                "discard" -> kept.remove(o.getString("id"))?.let { counts.merge(it, -1, Int::plus) }
            }
        }
    }

    private fun key(type: String, session: String, scene: String) = "$type/$session/$scene"

    private fun track(o: JSONObject) {
        val k = key(o.getString("type"), o.getString("session"), o.getString("scene"))
        counts.merge(k, 1, Int::plus); kept[o.getString("id")] = k
    }

    fun count(session: String, scene: String) = counts[key("trial", session, scene)] ?: 0
    /** Teach scans and the bed scan are session-independent: done once, like onboarding in the app. */
    fun done(type: String, scene: String) = (counts[key(type, SETUP, scene)] ?: 0) > 0

    @Synchronized fun append(o: JSONObject) {
        file.appendText(o.toString() + "\n")
        if (o.optString("type") in setOf("trial", "teach", "bedscan")) track(o)
    }

    @Synchronized fun discard(id: String, reason: String) {
        val k = kept.remove(id) ?: return
        file.appendText(JSONObject().put("type", "discard").put("id", id).put("reason", reason).put("at", System.currentTimeMillis()).toString() + "\n")
        counts.merge(k, -1, Int::plus)
    }

    companion object { const val SETUP = "setup" }
}
